#
# Makefile for mscviewer
#
# Oct 2011, Roberto Attias
#
# Copyright (c) 2010-2012 by Cisco Systems, Inc.
# All rights reserved.

#------------------------------------------------------------
# Parameters:
 
# Change JAVA_BIN var if your $JDK/bin is not in the path
JAVA_BIN = ""

# Change INSTALL_PREFIX if you don't want to install in $HOME
INSTALL_PREFIX := $(shell echo $$HOME)
#------------------------------------------------------------

ifeq ($(JAVA_BIN),"")
JAVAC = javac
JAVA = java
JAR = jar
else
JAVAC = $(JAVA_BIN)/javac
JAVA = $(JAVA_BIN)/java
JAR  = $(JAVA_BIN)/jar
endif

JYTHON_JAR:=jython-standalone-2.7.0.jar
SWINGX_JAR:=swingx-all-1.6.4.jar
UNAME:=$(shell uname -s)
MSCVER = $(shell cat src/com/cisco/mscviewer/Main.java |\
           grep VERSION | cut -d\" -f2)

TEXFLAGS = -interaction=nonstopmode -halt-on-error
CURDIR=$(shell pwd)

ifeq (,$(findstring CYGWIN,$(UNAME)))
  P:=:
else
  P:=;
endif
TEMPFILE1 := $(shell mktemp)
THIRD_PARTIES_PATH=$(INSTALL_DIR_N_FS)/third-parties

XJARS := $(THIRD_PARTIES_PATH)/$(JYTHON_JAR)$P$(THIRD_PARTIES_PATH)/$(SWINGX_JAR)
VERSIONED_NAME := mscviewer_$(MSCVER)
INSTALL_PREFIX := $(shell mktemp -d)
INSTALL_DIR := $(INSTALL_PREFIX)/$(VERSIONED_NAME)
ifeq (,$(findstring CYGWIN,$(UNAME)))
  INSTALL_DIR_N := $(INSTALL_DIR)
  INSTALL_DIR_N_FS := $(INSTALL_DIR)
  TEMPFILE := $(TEMPFILE1)
else
  INSTALL_DIR_N := $(shell cygpath -w $(INSTALL_DIR))
  INSTALL_DIR_N_FS := $(subst \,/,$(INSTALL_DIR_N))
  TEMPFILE := $(shell cygpath -m $(TEMPFILE1))
endif


.PHONY: all clean install jar distrib

distrib: banner jar manual-pdf release-help
	@#$(eval $(call setup_install_vars))
	@mkdir -p $(INSTALL_DIR)
	@cp -rf bin batch examples licenses third-parties $(INSTALL_DIR)
	@mkdir -p $(INSTALL_DIR)/doc/user-guide
	@cp -rf doc/manual/mscviewer.pdf $(INSTALL_DIR)/doc/user-guide
	@cp doc/release.html $(INSTALL_DIR)/doc
	@cp -rf mscviewer.jar resources $(INSTALL_DIR)
	@chmod 755 $(INSTALL_DIR)/bin/*
	@rm -rf $(INSTALL_DIR)/.[a-z]*
	@echo "*** Creating tgz distribution..."
	@tar cfz $(VERSIONED_NAME).tgz -C $(INSTALL_PREFIX) $(VERSIONED_NAME) &>.log/tar.log
	@echo "*** Creating zip distribution..."
	@cd $(INSTALL_PREFIX) ; zip -r $(VERSIONED_NAME).zip $(VERSIONED_NAME) &>$(CURDIR)/.log/zip.log
	@mv $(INSTALL_PREFIX)/$(VERSIONED_NAME).zip .
	@echo "*** removing temporary files..."
	@rm -rf $(INSTALL_DIR)
	@echo "Distribution creation completed."

release-help:
	@echo "*** Generating Release Help..."
	@mkdir -p doc
	@java -jar third-parties/jython-standalone-2.7.0.jar bin/github_milestones_history.py $(MSCVER) >doc/release.html

banner:
	@echo "Creating MSCViewer distribution. In case of error please"
	@echo "consult log files in .log/"
    
build:  
	@echo "*** Building mscviewer java code..."
	@mkdir -p .log
	@find  src -name *.java >.srclist
	@mkdir -p classes
	@$(JAVAC) -g -Xlint -classpath "src$Pthird-parties/$(SWINGX_JAR)$Pthird-parties/$(JYTHON_JAR)" -d classes @.srclist &>.log/java-build.log
	@$(JAVAC) -g -Xlint:-options -classpath "src" -d classes -source 1.3 src/com/cisco/mscviewer/TestVersion.java
	@mkdir -p classes/com/cisco/mscviewer
	-@cp -rf src/com/cisco/mscviewer/resources classes/com/cisco/mscviewer
	-@cp src/com/cisco/mscviewer/io/msc-session.dtd classes/com/cisco/mscviewer/io
	@echo "*** Generating Release Help..."



manual-pdf-gen: jar
	@rm -rf .log ; mkdir -p .log
	@mkdir -p doc/manual/generated; 
	@echo "*** Building PDF manual..."
	@echo "    - Capturing GUI screenshot for manual..."
	@bin/mscviewer --script batch/gen_manual_captures.py
	@echo "    - Generating title page..."
	@sed 's/\%VERSION\%/$(MSCVER)/g' doc/manual/titlepage.tex >doc/manual/generated/titlepage.tex
	@echo "    - Generating model API documentation..."
	@java -jar third-parties/$(JYTHON_JAR) bin/api2tex.py msc.model >doc/manual/generated/python-api-model.tex	
	@echo "    - Generating GUI API documentation..."
	@java -jar third-parties/$(JYTHON_JAR) bin/api2tex.py msc.gui >doc/manual/generated/python-api-gui.tex
	@echo "    - Generating graph API documentation..."
	@java -jar third-parties/$(JYTHON_JAR) bin/api2tex.py msc.graph >doc/manual/generated/python-api-graph.tex   
	@echo "    - Generating flow API documentation..."
	@java -jar third-parties/$(JYTHON_JAR) bin/api2tex.py msc.flowdef >doc/manual/generated/python-api-flowdef.tex   

manual-pdf-build: 
	@echo "    - compiling documentation..."       
	@cd doc/manual ; echo "\tableofcontents" >tocnotoc.tex; pdflatex $(TEXFLAGS) mscviewer.tex &> ../../.log/manual-pdf-1.log; pdflatex $(TEXFLAGS) mscviewer.tex &> ../../.log/manual-pdf-2.log
	@cd doc/manual ; rm -f *.4* *.aux *.css *.dvi *.idv *.idx *.lg *.log *.out *.tmp *.xref *.toc

manual-pdf: manual-pdf-gen manual-pdf-build

manual-html:
	@rm -rf .log ; mkdir -p .log
	@echo "*** Building HTML manual..."
	@cd doc/manual/ ; echo "" >tocnotoc.tex ; htlatex mscviewer.tex "" "" "" $(TEXFLAGS) &> ../../.log/manual-html.log
	@cd doc/manual ; rm -f *.4* *.aux *.css *.dvi *.idv *.idx *.lg *.log *.out *.tmp *.xref *.toc

manual-python-api:
	@rm -rf .log ; mkdir -p .log
	@echo "*** Building Python API documentation..."
	@cd doc/python-api ; ./buildit.sh latexpdf &>../../.log/manual-python-api-latexpdf.log
	@cd doc/python-api ; ./buildit.sh html &> ../../.log/manual-python-api-html.log


clean:
	-@rm -rf classes .srclist
	-@rm -rf doc/manual/*.svg doc/manual/*.html doc/manual/*.png doc/manual/*.pdf doc/manual/tocnotoc.tex 
	-@rm -rf $(INSTALL_DIR)
	-@rm -f $(WS_TOOLS_DIR)/host_tools.$(TARGET).sentinel 
	-@rm -f mscviewer*.tgz mscviewer*.zip mscviewer*.jar
	-@rm -f *.tmp

jar: build 
	@echo "*** Packaging classes to jar file..."
	$(eval $(call create_temp_file))
	@echo "Manifest-Version: 1.1" >$(TEMPFILE)
	@echo "Created-By: Rattias" >>$(TEMPFILE)
	@echo "Class-Path: third-parties/$(JYTHON_JAR) third-parties/$(SWINGX_JAR)" >>$(TEMPFILE)
	@echo "Main-Class: com.cisco.mscviewer.Main" >>$(TEMPFILE1)
	@echo >>$(TEMPFILE)
	@$(JAR) cmf $(TEMPFILE) mscviewer.jar -C classes .
	@rm $(TEMPFILE)
    

		
