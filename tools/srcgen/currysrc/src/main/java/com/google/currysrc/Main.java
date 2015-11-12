/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.currysrc;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import com.google.currysrc.api.Rules;
import com.google.currysrc.api.input.InputFileGenerator;
import com.google.currysrc.api.output.OutputSourceFileGenerator;
import com.google.currysrc.api.transform.TransformRule;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The main execution API for users of currysrc.
 */
public final class Main {

  private static final Charset JAVA_SOURCE_CHARSET = StandardCharsets.UTF_8;

  private final boolean debug;

  public Main(boolean debug) {
    this.debug = debug;
  }

  public void execute(Rules rules) throws Exception {
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);

    InputFileGenerator inputFileGenerator = rules.getInputFileGenerator();
    OutputSourceFileGenerator outputSourceFileGenerator = rules.getOutputSourceFileGenerator();
    for (File inputFile : inputFileGenerator.generate()) {
      System.out.println("Processing: " + inputFile);

      String source = readSource(inputFile);
      CompilationUnitHandler compilationUnitHandler =
          new CompilationUnitHandler(inputFile, parser, source);
      compilationUnitHandler.setDebug(debug);

      List<TransformRule> transformRules = rules.getTransformRules(inputFile);
      if (!transformRules.isEmpty()) {
        for (TransformRule transformRule : transformRules) {
          compilationUnitHandler.apply(transformRule);
        }
      }

      File outputFile = outputSourceFileGenerator.generate(
          compilationUnitHandler.getCompilationUnit(), inputFile);
      if (outputFile != null) {
        writeSource(compilationUnitHandler.getDocument(), outputFile);
      }
    }
  }

  private static void writeSource(Document document, File outputFile) throws IOException {
    File outputDir = outputFile.getParentFile();
    if (outputDir.exists()) {
      if (!outputDir.isDirectory()) {
        throw new IOException(outputDir + " is not a directory");
      }
    }
    if (!outputDir.exists()) {
      if (!outputDir.mkdirs()) {
        throw new IOException("Unable to create " + outputDir);
      }
    }
    String source = document.get();

    // TODO Look at guava for this
    FileOutputStream fos = new FileOutputStream(outputFile);
    Writer writer = new OutputStreamWriter(fos, JAVA_SOURCE_CHARSET);
    try {
      writer.write(source.toCharArray());
    } finally {
      writer.close();
    }
  }

  private static String readSource(File file) throws IOException {
    StringBuilder sb = new StringBuilder(2048);
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(file), JAVA_SOURCE_CHARSET))) {
      char[] buffer = new char[1024];
      int count;
      while ((count = reader.read(buffer)) != -1) {
        sb.append(buffer, 0, count);
      }
    }
    return sb.toString();
  }

  private static class CompilationUnitHandler implements TransformRule.Context {

    private final File file;
    private final ASTParser parser;
    private boolean debug;

    private Document documentBefore;
    private CompilationUnit compilationUnitBefore;

    private Document documentRequested;
    private TrackingASTRewrite rewriteRequested;

    public CompilationUnitHandler(File file, ASTParser parser, String source) {
      this.file = file;
      this.parser = parser;

      // Initialize source / AST state.
      documentBefore = new Document(source);
      compilationUnitBefore = parseDocument(file, parser, documentBefore);
    }

    public void setDebug(boolean debug) {
      this.debug = debug;
    }

    public void apply(TransformRule transformRule) throws BadLocationException {
      if (documentRequested != null || rewriteRequested != null) {
        throw new AssertionError("Handler state not reset properly");
      }

      if (transformRule.matches(compilationUnitBefore)) {
        // Apply the rule.
        transformRule.transform(this, compilationUnitBefore);

        // Work out what happened, report/error as needed and reset the state.
        CompilationUnit compilationUnitAfter;
        Document documentAfter;
        if (ruleUsedRewrite()) {
          if (debug) {
            System.out.println("AST Transform: " + transformRule + ", rewrite: " +
                (rewriteRequested.isEmpty() ? "None" : rewriteRequested.toString()));
          }
          if (rewriteRequested.isEmpty()) {
            if (transformRule.mustModify()) {
              throw new RuntimeException("AST Transformer Rule: " + transformRule
                  + " did not modify the compilation unit as it should");
            }
            documentAfter = documentBefore;
            compilationUnitAfter = compilationUnitBefore;
          } else {
            Document documentToRewrite = new Document(documentBefore.get());
            compilationUnitAfter = applyRewrite(file + " after " + transformRule, parser,
                documentToRewrite, rewriteRequested);
            documentAfter = documentToRewrite;
          }
        } else if (ruleUsedDocument()) {
          String sourceBefore = documentBefore.get();
          String sourceAfter = documentRequested.get();
          if (debug) {
            System.out.println(
                "Document Transformer: " + transformRule + ", diff: " +
                    generateDiff(sourceBefore, sourceAfter));
          }
          if (sourceBefore.equals(sourceAfter)) {
            if (transformRule.mustModify()) {
              throw new RuntimeException("Document Transformer Rule: " + transformRule
                  + " did not modify document as it should");
            }
            documentAfter = documentBefore;
            compilationUnitAfter = compilationUnitBefore;
          } else {
            // Regenerate the AST from the modified document.
            compilationUnitAfter = parseDocument(
                file + " after document transformer " + transformRule, parser, documentRequested);
            documentAfter = documentRequested;
          }
        } else {
          // The rule didn't request anything.... should this be an error?
          compilationUnitAfter = compilationUnitBefore;
          documentAfter = documentBefore;
        }

        // Reset document / compilation state for the next round.
        documentBefore = documentAfter;
        compilationUnitBefore = compilationUnitAfter;
        documentRequested = null;
        rewriteRequested = null;
      }
    }

    @Override public ASTRewrite rewrite() {
      if (documentRequested != null) {
        throw new IllegalStateException("document() already called.");
      }
      if (rewriteRequested != null) {
        throw new IllegalStateException("rewrite() already called.");
      }
      rewriteRequested = createTrackingASTRewrite(compilationUnitBefore);
      return rewriteRequested;
    }

    @Override public Document document() {
      if (rewriteRequested != null) {
        throw new IllegalStateException("rewrite() already called.");
      }
      if (documentRequested != null) {
        throw new IllegalStateException("document() already called.");
      }
      documentRequested = new Document(documentBefore.get());
      return documentRequested;
    }

    public CompilationUnit getCompilationUnit() {
      return compilationUnitBefore;
    }

    public Document getDocument() {
      return documentBefore;
    }

    private boolean ruleUsedRewrite() {
      return rewriteRequested != null;
    }

    private boolean ruleUsedDocument() {
      return documentRequested != null;
    }

    private static CompilationUnit applyRewrite(Object documentId, ASTParser parser,
        Document document, ASTRewrite rewrite) throws BadLocationException {
      TextEdit textEdit = rewrite.rewriteAST(document, null);
      textEdit.apply(document, TextEdit.UPDATE_REGIONS);
      // Reparse the document.
      return parseDocument(documentId, parser, document);
    }

    private static CompilationUnit parseDocument(Object documentId, ASTParser parser,
        Document document) {
      parser.setSource(document.get().toCharArray());
      configureParser(parser);

      CompilationUnit cu = (CompilationUnit) parser.createAST(null /* progressMonitor */);
      if (cu.getProblems().length > 0) {
        System.err.println("Error parsing:" + documentId + ": " + Arrays.toString(cu.getProblems()));
        throw new RuntimeException("Unable to parse document. Stopping.");
      }
      return cu;
    }

    private static void configureParser(ASTParser parser) {
      Map<String, String> options = JavaCore.getOptions();
      options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_7);
      options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_7);
      options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
      parser.setCompilerOptions(options);
    }

    private static TrackingASTRewrite createTrackingASTRewrite(CompilationUnit cu) {
      return new TrackingASTRewrite(cu.getAST());
    }

    private static String generateDiff(String before, String after) {
      if (before.equals(after)) {
        return "No diff";
      }
      // TODO Implement this
      return "Diff. DIFF NOT IMPLEMENTED";
    }
  }

  private static class TrackingASTRewrite extends ASTRewrite {

    public TrackingASTRewrite(AST ast) {
      super(ast);
    }

    public boolean isEmpty() {
      return !getRewriteEventStore().getChangeRootIterator().hasNext();
    }
  }
}
