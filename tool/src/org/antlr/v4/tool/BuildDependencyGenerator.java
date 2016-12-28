/*
 * [The "BSD license"]
 *  Copyright (c) 2016 Mike Lischke
 *  Copyright (c) 2012 Terence Parr
 *  Copyright (c) 2012 Sam Harwell
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 *  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.antlr.v4.tool;

import org.antlr.v4.Tool;
import org.antlr.v4.codegen.CodeGenerator;
import org.antlr.v4.codegen.Target;
import org.antlr.v4.parse.ANTLRParser;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Given a grammar file, show the dependencies on .tokens etc...
 *  Using ST, emit a simple "make compatible" list of dependencies.
 *  For example, combined grammar T.g (no token import) generates:
 *
 *  	TParser.java : T.g
 *  	T.tokens : T.g
 *  	TLexer.java : T.g
 *
 *  If we are using the listener pattern (-listener on the command line)
 *  then we add:
 *
 *      TListener.java : T.g
 *      TBaseListener.java : T.g
 *
 *  If we are using the visitor pattern (-visitor on the command line)
 *  then we add:
 *
 *      TVisitor.java : T.g
 *      TBaseVisitor.java : T.g
 *
 *  If "-lib libdir" is used on command-line with -depend and option
 *  tokenVocab=A in grammar, then include the path like this:
 *
 * 		T.g: libdir/A.tokens
 *
 *  Pay attention to -o as well:
 *
 * 		outputdir/TParser.java : T.g
 *
 *  So this output shows what the grammar depends on *and* what it generates.
 *
 *  Operate on one grammar file at a time.  If given a list of .g on the
 *  command-line with -depend, just emit the dependencies.  The grammars
 *  may depend on each other, but the order doesn't matter.  Build tools,
 *  reading in this output, will know how to organize it.
 *
 *  This code was obvious until I removed redundant "./" on front of files
 *  and had to escape spaces in filenames :(
 *
 *  I literally copied from v3 so might be slightly inconsistent with the
 *  v4 code base.
 */
public class BuildDependencyGenerator {
    protected Tool tool;
    protected Grammar g;
    protected CodeGenerator generator;
    protected STGroup templates;

    public BuildDependencyGenerator(Tool tool, Grammar g) {
        this.tool = tool;
		this.g = g;
		String language = g.getOptionString("language");
		generator = new CodeGenerator(tool, g, language);
    }

    /** From T.g return a list of File objects that
     *  name files ANTLR will emit from T.g.
     */
    public List<File> getGeneratedFileList() {
		Target target = generator.getTarget();
		if (target == null) {
			// if the target could not be loaded, no code will be generated.
			return new ArrayList<File>();
		}

        List<File> files = new ArrayList<File>();

        // add generated recognizer; e.g., TParser.java
        if (generator.getTarget().needsHeader()) {
          files.add(getOutputFile(generator.getRecognizerFileName(true)));
        }
        files.add(getOutputFile(generator.getRecognizerFileName(false)));
        // add output vocab file; e.g., T.tokens. This is always generated to
        // the base output directory, which will be just . if there is no -o option
        //
		files.add(getOutputFile(generator.getVocabFileName()));
		// are we generating a .h file?
        ST headerExtST = null;
        ST extST = target.getTemplates().getInstanceOf("codeFileExtension");
        if (target.getTemplates().isDefined("headerFile")) {
            headerExtST = target.getTemplates().getInstanceOf("headerFileExtension");
            String suffix = Grammar.getGrammarTypeToFileNameSuffix(g.getType());
            String fileName = g.name + suffix + headerExtST.render();
            files.add(getOutputFile(fileName));
        }
        if ( g.isCombined() ) {
            // add autogenerated lexer; e.g., TLexer.java TLexer.h TLexer.tokens

            String suffix = Grammar.getGrammarTypeToFileNameSuffix(ANTLRParser.LEXER);
            String lexer = g.name + suffix + extST.render();
            files.add(getOutputFile(lexer));
            String lexerTokens = g.name + suffix + CodeGenerator.VOCAB_FILE_EXTENSION;
            files.add(getOutputFile(lexerTokens));

            // TLexer.h
            if (headerExtST != null) {
                String header = g.name + suffix + headerExtST.render();
                files.add(getOutputFile(header));
            }
        }

        if ( g.tool.gen_listener ) {
          // add generated listener; e.g., TListener.java
          if (generator.getTarget().needsHeader()) {
            files.add(getOutputFile(generator.getListenerFileName(true)));
          }
          files.add(getOutputFile(generator.getListenerFileName(false)));

          // add generated base listener; e.g., TBaseListener.java
          if (generator.getTarget().needsHeader()) {
            files.add(getOutputFile(generator.getBaseListenerFileName(true)));
          }
          files.add(getOutputFile(generator.getBaseListenerFileName(false)));
        }

        if ( g.tool.gen_visitor ) {
          // add generated visitor; e.g., TVisitor.java
          if (generator.getTarget().needsHeader()) {
            files.add(getOutputFile(generator.getVisitorFileName(true)));
          }
          files.add(getOutputFile(generator.getVisitorFileName(false)));

          // add generated base visitor; e.g., TBaseVisitor.java
          if (generator.getTarget().needsHeader()) {
            files.add(getOutputFile(generator.getBaseVisitorFileName(true)));
          }
          files.add(getOutputFile(generator.getBaseVisitorFileName(false)));
        }


		// handle generated files for imported grammars
		List<Grammar> imports = g.getAllImportedGrammars();
		if ( imports!=null ) {
			for (Grammar g : imports) {
//				File outputDir = tool.getOutputDirectory(g.fileName);
//				String fname = groomQualifiedFileName(outputDir.toString(), g.getRecognizerName() + extST.render());
//				files.add(new File(outputDir, fname));
				files.add(getOutputFile(g.fileName));
			}
		}

		if (files.isEmpty()) {
			return null;
		}
		return files;
	}

	public File getOutputFile(String fileName) {
		File outputDir = tool.getOutputDirectory(g.fileName);
		if ( outputDir.toString().equals(".") ) {
			// pay attention to -o then
			outputDir = tool.getOutputDirectory(fileName);
		}
		if ( outputDir.toString().equals(".") ) {
			return new File(fileName);
		}
		if (outputDir.getName().equals(".")) {
			String fname = outputDir.toString();
			int dot = fname.lastIndexOf('.');
			outputDir = new File(outputDir.toString().substring(0,dot));
		}

		if (outputDir.getName().indexOf(' ') >= 0) { // has spaces?
			String escSpaces = outputDir.toString().replace(" ", "\\ ");
			outputDir = new File(escSpaces);
		}
		return new File(outputDir, fileName);
	}

    /**
     * Return a list of File objects that name files ANTLR will read
     * to process T.g; This can be .tokens files if the grammar uses the tokenVocab option
     * as well as any imported grammar files.
     */
    public List<File> getDependenciesFileList() {
        // Find all the things other than imported grammars
        List<File> files = getNonImportDependenciesFileList();

        // Handle imported grammars
        List<Grammar> imports = g.getAllImportedGrammars();
        if ( imports!=null ) {
			for (Grammar g : imports) {
				String libdir = tool.libDirectory;
				String fileName = groomQualifiedFileName(libdir, g.fileName);
				files.add(new File(fileName));
			}
		}

        if (files.isEmpty()) {
            return null;
        }
        return files;
    }

    /**
     * Return a list of File objects that name files ANTLR will read
     * to process T.g; This can only be .tokens files and only
     * if they use the tokenVocab option.
     *
     * @return List of dependencies other than imported grammars
     */
    public List<File> getNonImportDependenciesFileList() {
        List<File> files = new ArrayList<File>();

        // handle token vocabulary loads
        String tokenVocab = g.getOptionString("tokenVocab");
        if (tokenVocab != null) {
			String fileName =
				tokenVocab + CodeGenerator.VOCAB_FILE_EXTENSION;
			File vocabFile;
			if ( tool.libDirectory.equals(".") ) {
				vocabFile = new File(fileName);
			}
			else {
				vocabFile = new File(tool.libDirectory, fileName);
			}
			files.add(vocabFile);
		}

        return files;
    }

    public ST getDependencies() {
        loadDependencyTemplates();
        ST dependenciesST = templates.getInstanceOf("dependencies");
        dependenciesST.add("in", getDependenciesFileList());
        dependenciesST.add("out", getGeneratedFileList());
        dependenciesST.add("grammarFileName", g.fileName);
        return dependenciesST;
    }

    public void loadDependencyTemplates() {
        if (templates != null) return;
        String fileName = "org/antlr/v4/tool/templates/depend.stg";
        templates = new STGroupFile(fileName, "UTF-8");
    }

    public CodeGenerator getGenerator() {
        return generator;
    }

    public String groomQualifiedFileName(String outputDir, String fileName) {
        if (outputDir.equals(".")) {
            return fileName;
        }
		else if (outputDir.indexOf(' ') >= 0) { // has spaces?
            String escSpaces = outputDir.replace(" ", "\\ ");
            return escSpaces + File.separator + fileName;
        }
		else {
            return outputDir + File.separator + fileName;
        }
    }
}
