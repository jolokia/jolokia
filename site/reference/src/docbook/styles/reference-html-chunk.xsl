<?xml version="1.0"?>
<!-- 
    This is the XSL HTML configuration file for the Citrus  Reference Documentation.
-->
<!DOCTYPE xsl:stylesheet [
]>

<xsl:stylesheet xmlns="http://www.w3.org/TR/xhtml1/transitional"
                xmlns:xslthl="http://xslthl.sf.net"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="1.0"
                exclude-result-prefixes="#default xslthl">
                
    <xsl:import href="../lib/docbook-xsl/html/chunk.xsl"/>
    <xsl:import href="../lib/docbook-xsl/html/highlight.xsl"/>

<!--###################################################
                     HTML Settings
    ################################################### -->   
    <xsl:param name="chunk.section.depth">'5'</xsl:param>
    <xsl:param name="use.id.as.filename">'1'</xsl:param>
    <xsl:param name="html.stylesheet">reference-html.css</xsl:param>

    <!-- These extensions are required for table printing and other stuff -->
    <xsl:param name="use.extensions">1</xsl:param>
    <xsl:param name="tablecolumns.extension">0</xsl:param>
    <xsl:param name="callout.extensions">1</xsl:param>
    <xsl:param name="graphicsize.extension">0</xsl:param>

<!--###################################################
                      Table Of Contents
    ################################################### -->   

    <!-- Generate the TOCs for named components only -->
    <xsl:param name="generate.toc">
        book   toc
        qandaset  toc
    </xsl:param>
    
    <!-- Show only Sections up to level 3 in the TOCs -->
    <xsl:param name="toc.section.depth">3</xsl:param>
    
<!--###################################################
                         Labels
    ################################################### -->   

    <!-- Label Chapters and Sections (numbering) -->
    <xsl:param name="chapter.autolabel">1</xsl:param>
    <xsl:param name="section.autolabel" select="1"/>
    <xsl:param name="section.label.includes.component.label" select="1"/>

<!--###################################################
                         Callouts
    ################################################### -->   

    <!-- Use images for callouts instead of (1) (2) (3) -->
    <xsl:param name="callout.graphics">1</xsl:param>
    <xsl:param name="callout.graphics.path">images/callouts/</xsl:param>
    
    <!-- Place callout marks at this column in annotated areas -->
    <xsl:param name="callout.defaultcolumn">90</xsl:param>

<!--###################################################
                       Admonitions
    ################################################### -->   

    <!-- Use nice graphics for admonitions -->
    <xsl:param name="admon.graphics">'1'</xsl:param>
    <xsl:param name="admon.graphics.path">images/admons/</xsl:param>
            
<!--###################################################
                          Misc
    ################################################### -->
    
    <xsl:param name="draft.mode">no</xsl:param>
    
    <!-- Placement of titles -->
    <xsl:param name="formal.title.placement">
        figure after
        example before
        equation before
        table before
        procedure before
    </xsl:param>
    
    <xsl:template name="book.titlepage.separator">
        <hr/>
        <img src="images/citrus_logo.png" style="width:25%;float:right;"/>
    </xsl:template>
    
    <xsl:template match="author" mode="titlepage.mode">
        <xsl:if test="name(preceding-sibling::*[1]) = 'author'">
            <xsl:text>, </xsl:text>
        </xsl:if>
        <span class="{name(.)}">
            <xsl:call-template name="person.name" />
            <xsl:apply-templates mode="titlepage.mode" select="./contrib" />
            <xsl:apply-templates mode="titlepage.mode" select="./affiliation" />
        </span>
    </xsl:template>
    <xsl:template match="authorgroup" mode="titlepage.mode">
        <div class="{name(.)}">
            <h2>Authors</h2>
            <p/>
            <xsl:apply-templates mode="titlepage.mode" />
        </div>
    </xsl:template>

<!--###################################################
                       Highlighting
    ################################################### -->
    
    <xsl:param name="highlight.source">1</xsl:param>
    <xsl:param name="highlight.default.language">xml</xsl:param>
    
    <xsl:template match="xslthl:tag" mode="xslthl">
        <span class="hl-tag"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>
    
    <xsl:template match='xslthl:attribute' mode="xslthl">
      <span class="hl-attribute"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>
    
    <xsl:template match='xslthl:value' mode="xslthl">
      <span class="hl-value"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>
      
</xsl:stylesheet>