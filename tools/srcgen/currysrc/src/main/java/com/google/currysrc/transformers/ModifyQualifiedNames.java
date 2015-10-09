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
package com.google.currysrc.transformers;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import com.google.currysrc.api.transform.AstTransformer;

/**
 * Changes any qualified names in the AST that start with {@code oldPrefix} to start with
 * {@code newPrefix} instead.
 */
public class ModifyQualifiedNames implements AstTransformer {

  private final String oldPrefix;

  private final String newPrefix;

  public ModifyQualifiedNames(String oldPrefix, String newPrefix) {
    this.oldPrefix = oldPrefix;
    this.newPrefix = newPrefix;
  }

  @Override
  public void transform(CompilationUnit cu, final ASTRewrite rewrite) {
    ASTVisitor visitor = new ASTVisitor(true /* visitDocTags */) {
      @Override
      public boolean visit(QualifiedName node) {
        Name qualifier = node.getQualifier();
        if (qualifier != null) {
          String fullyQualifiedName = qualifier.getFullyQualifiedName();
          if (fullyQualifiedName.startsWith(oldPrefix)) {
            String newQualifierString = newPrefix + fullyQualifiedName
                .substring(oldPrefix.length());
            Name newQualifier = node.getAST().newName(newQualifierString);
            rewrite.replace(qualifier, newQualifier, null /* editGroup */);
          }
        }
        return false;
      }
    };
    cu.accept(visitor);
  }

  @Override
  public String toString() {
    return "ModifyQualifiedNames{" +
        "oldPrefix='" + oldPrefix + '\'' +
        ", newPrefix='" + newPrefix + '\'' +
        '}';
  }
}
