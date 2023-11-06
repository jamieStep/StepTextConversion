/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperEnhancedUsx
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.getExtendedNodeName
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.reportBookBeingProcessed
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.ref.*
import org.stepbible.textconverter.support.shared.SharedData
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.C_CollapseSubverses
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.BufferedWriter
import java.io.File
import java.io.Writer
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory


/******************************************************************************/
/**
 * A class which converts a collection of USX version 3 files (or at least, my
 * enhanced version) to OSIS.
 *
 * The USX-to-OSIS mappings are based mainly upon some suggestions for
 * USFM-to-OSIS mappings in the OSIS 2.1.1 reference manual, Appendix F.
 * (I must admit, however, that I am not sure I have fully understood what
 * that says, so some further refinement may be necessary.)
 *
 * @author ARA "Jamie" Jamieson
 */

object TextConverterProcessorEnhancedUsxToOsis : TextConverterProcessorBase()
 {
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Package                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner (): String
  {
    return "Converting enhanced USX to OSIS"
  }


  /****************************************************************************/
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
    if (C_InputType != InputType.USX) return true
    deleteFiles(listOf(Pair(StandardFileLocations.getOsisFolderPath(), null)))
    createFolders(listOf(StandardFileLocations.getOsisFolderPath()))
    return true
  }


  /****************************************************************************/
  override fun process (): Boolean
  {
    doProcess()
    return true
  }
  

  /****************************************************************************/
  override fun runMe (): Boolean
  {
    return C_InputType == InputType.USX
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Converts a collection of enhanced USX files to OSIS.
   */

  private fun doProcess ()
  {
    /**************************************************************************/
    // XXXBibleStructure.UsxUnderConstructionInstance().populate(StandardFileLocations.getEnhancedUsxFolderPath(), false)



    /**************************************************************************/
    Files.newBufferedWriter(Paths.get(StandardFileLocations.getOsisFilePath())).use { fOut -> // The output OSIS file.
      m_Out = fOut
      fileHeader(m_Out)
      BibleBookAndFileMapperEnhancedUsx.iterateOverSelectedFiles(::processFile)
      fileTrailer(m_Out)
    }
    
    
    
    /**************************************************************************/
    /* Post process to sort out line breaks. */

    processLineBreaks()
    
    
    
    /**************************************************************************/
    /* Validate against the XML schema, unless we've done things which we know
       ahead of time will have generated invalid XML.
    
       REVISED: At present, we've given up the validation.  This is unfortunate,
       because it means we are producing modules which Crosswire won't accept,
       but there are now quite a lot of circumstances where we may potentially
       need to break the OSIS rules, and it's just too fiddly to decide when to
       validate and when not.       
    */
    
    val C_DoValidation = false
    if (C_DoValidation)
    {
      val validationErrors = validateXmlAgainstSchema(StandardFileLocations.getOsisFilePath())
      if (null != validationErrors) Logger.error(validationErrors)
    }
  }


  /****************************************************************************/
  private fun getOsisXsdLocation (): String
  {
    return ConfigData["stepExternalDataPath_OsisXsd"]!! // With Crosswire tweaks.
  }


  /****************************************************************************/
  /* Have to admit I can't recall what this is supposed to achieve.  I think we
    can assume it's here to delete excess vertical whitespace, and it is
    _probably_ motivated by some of the stuff in Appendix F of the OSIS
    reference manual. */

  private fun processLineBreaks ()
  {
    /**************************************************************************/
    val dom: Document = Dom.getDocument(StandardFileLocations.getOsisFilePath())



    /**************************************************************************/
    fun processLineBreak (node: Node)
    {
      var okToDelete = true
      var n: Node? = node
      while (true)
      {
        n = Dom.getPreviousSibling(n!!) ?: break
        if (Dom.isWhitespace(n)) continue
        val nodeName: String = Dom.getNodeName(n)
        if ("p" == nodeName) break
        if ("l" == nodeName && !n.hasChildNodes()) continue
        if ("verse" == nodeName) continue
        okToDelete = false
        break
      }

      if (!okToDelete) return

      n = node
      while (true)
      {
        n = Dom.getPreviousSibling(n!!) ?: break
        val nodeName: String = Dom.getNodeName(n)
        if (Dom.isWhitespace(n)) continue
        if ("p" == nodeName) break
        if ("l" == nodeName && !n.hasChildNodes()) continue
        if ("verse" == nodeName) continue
        okToDelete = false
        break
      }

      if (okToDelete) Dom.deleteNode(node)
    }



    /**************************************************************************/
    val lineBreaks = Dom.findNodesByName(dom.documentElement, "l", false)
        .filter { "l" == Dom.getNodeName(it) && !it.hasChildNodes() }
    lineBreaks.forEach { processLineBreak(it) }
    Dom.outputDomAsXml(dom, StandardFileLocations.getOsisFilePath(), null)
  }


  /****************************************************************************/
  /**
   * Generates the file header.
   *
   * @param fOut Where to write to.
   */

  private fun fileHeader (fOut: Writer)
  {
    /**************************************************************************/
    /* Note: Near the bottom of this header, I have a work and a workPrefix tag
       which ostensibly are needed to support Strong's.  I have no idea whether
       this is actually the case, nor, in fact what they should look like (I
       imagine, for instance, that the work tag should contain some
       sub-elements, but the documentation gives no examples.)  So I may be
       wrong that they are needed; and if needed, I may have got them wrong
       (except that things seem to have worked ok with at least one Strong's
       text I've worked with).  And I also don't know whether they should be
       included _only_ on texts which have Strongs.  (I do have earlier
       processing which sets a flag in SharedData/FeatureIdentifier to flag
       where we have Strongs, although ideally support for that should be
       removed if it looks as though we can safely include the Strong's stuff
       come what may. */

    var header = """<?xml version='1.0' encoding='UTF-8'?>
<osis xmlns='http://www.bibletechnologies.net/2003/OSIS/namespace'
      xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'
      xsi:schemaLocation='http://www.bibletechnologies.net/2003/OSIS/namespace
                          ${'$'}xsdLocation'>
  <!-- OSIS generated by the STEP project (www.stepbible.org) ${'$'}todaysDate -->
  <osisText osisIDWork='${'$'}moduleName' osisRefWork='${'$'}toLowerPublicationType' xml:lang='${'$'}languageCode' canonical='true'>
    <header>
      ${'$'}revisionDesc
      <work osisWork='${'$'}moduleName'>
        <title>${'$'}title</title>
        <type type='OSIS'>${'$'}publicationType</type>
        <identifier type='OSIS'>${'$'}publicationType.${'$'}languageCode.${'$'}moduleName.${'$'}yearOfText</identifier>
        <rights type='x-copyright'>${'$'}shortCopyright</rights>
        <scope>${'$'}scope</scope>
        <refSystem>${'$'}publicationType</refSystem>
      </work>
      <work osisWork='${'$'}toLowerPublicationType'>
        <type type='OSIS'>${'$'}publicationType</type>
        <refSystem>Bible</refSystem>
      </work>
      <work osisWork='strong'/> <!-- Strongs.  I'm not sure whether there's a problem including this even on texts which don't need it; and I'm not actually sure it's needed at all. -->
    </header>"""
    
    header = header.replace("$", "\u0001")
    
    
    
    /**************************************************************************/
    /* Fill in details in the standard header string.  A few notes ...
    
       * revisionDesc: Keeps tracks of revisions.  I doubt this information will
         be available to us in any meaningful form, for which reason I have not
         attempted to do anything 'proper' with it.  This means that at present
         it would need to be supplied in the configuration information in the
         format required by the OSIS reference manual, qv.
    */
    
    var yearOfText: String = ConfigData["stepTextModifiedDate"]!!
    if (yearOfText.contains("-")) yearOfText = yearOfText.split("-")[2]

    var publicationType = if ("bible" == (ConfigData["stepTypeOfDocument"] ?: "bible").lowercase()) "zText" else "zCom"
    publicationType = if (publicationType.equals("zText", ignoreCase = true)) "Bible" else "Commentary"

    val vals: MutableMap<String, String?> = HashMap()
    vals["languageCode"] = ConfigData["stepLanguageCode3Char"]
    vals["moduleName"] = ConfigData["stepModuleName"]
    vals["publicationType"] = publicationType
    vals["revisionDesc"] = ConfigData["revisionDesc"] ?: "" // See note above.
    vals["scope"] = makeScope()
    vals["shortCopyright"] = ConfigData["stepShortCopyright"]!!.replace("<.*?>".toRegex(), " ").replace("&copy;", "(c)")
    vals["title"] = ConfigData.makeStepDescription()
    vals["toLowerPublicationType"] = publicationType.lowercase(Locale.getDefault())
    vals["todaysDate"] = SimpleDateFormat("dd-MMM-yy").format(Date())
    vals["xsdLocation"] = getOsisXsdLocation()
    vals["yearOfText"] = yearOfText



    /**************************************************************************/
    if (null == vals["scope"])
    {
      vals.remove("scope")
      header = header.replace("<scope>\u0001scope</scope>".toRegex(), "")
    }



    /**************************************************************************/
    for (key in vals.keys) header = header.replace("\u0001" + key, vals[key]!!)
    if (header.contains("\u0001")) throw StepException("OSIS header: incomplete substitution.")
    header = header.replace("(?m)^[ \\t]*\\r?\\n".toRegex(), "").replace("\n\n", "\n")
    fOut.write(header)
  }


  /****************************************************************************/
  private fun fileTrailer (fOut: Writer)
  {
    fOut.write("</osisText>\n")
    fOut.write("</osis>\n")
  }


  /****************************************************************************/
  /* Called to handle any "@" variables in the tag translation details. */

  private fun fillInAttributes (theTags: String, node: Node): String
  {
    /**************************************************************************/
    fun getRefBasedAttributeValue (attribName: String): String
    {
      var attributeName = attribName
      var attributeValue = ""
      var formatDetails: String? = null
      val convertRefToOsis = attributeName.lowercase().startsWith("asosisref_")
      if (convertRefToOsis) attributeName = attributeName.substring("AsOsisRef_".length)
      val convertRefToVernacular = attributeName.lowercase().startsWith("asvernacularref_")

      if (convertRefToVernacular)
      {
        attributeName = attributeName.substring("asvernacularref_".length)
        val ix = attributeName.indexOf("_")
        if (ix < 0)
          formatDetails = "bcv"
        else
        {
          formatDetails = attributeName.split("_")[0]
          attributeName = attributeName.split("_")[1]
        }
      }

      if (Dom.hasAttribute(node, attributeName))
        attributeValue = Dom.getAttribute(node, attributeName)!!

      val rc = RefCollection.rdUsx(attributeValue)
      if (convertRefToOsis) attributeValue = rc.getElements()[0].toStringOsis() else if (convertRefToVernacular) attributeValue = rc.toStringVernacular(formatDetails!!, null)
      return attributeValue
    }


    /**************************************************************************/
    var tags = theTags

    while (true)
    {
      val m = C_Pattern_AttributesInTagDetails.matcher(tags)
      if (!m.find()) break

      var attributeValue: String?
      var attributeName = m.group(1)
      when (attributeName)
      {
        "allAttributes" ->
        {
          attributeValue = ""; Dom.getAttributes(node).forEach { attributeValue += " " + it.key + "='" + it.value + "'" }
          attributeValue = attributeValue!!.substring(1)
        }


        "currentChapter" ->
         {
          val ref: Ref = Ref.rd(m_CurrentVerseLow)
          if (!ref.hasC()) ref.setC(0)
          ref.clearV()
          ref.clearS()
          attributeValue = ref.toStringOsis()
          if (0 == ref.getC()) attributeValue += ".0" // Force chapter zero for use in introductions.
        }


        "currentChapterVerseZero" ->
        {
          val ref = Ref.rd(m_CurrentVerseLow)
          if (!ref.hasC()) ref.setC(0)
          ref.setV(0)
          ref.clearS()
          attributeValue = ref.toStringOsis() + ".0.0"
        }


        "currentRefLow" ->
        {
          attributeValue = m_CurrentVerseLow.toStringOsis()
        }


        "currentRefLow_b" ->
        {
          attributeValue = m_CurrentVerseLow.toStringOsis("b")
        }


        "currentRefLow_bc" ->
        {
          attributeValue = m_CurrentVerseLow.toStringOsis("bc")
        }


        "currentRefAsRange" ->
        {
          val refs = RefRange(m_CurrentVerseLow, m_CurrentVerseHigh)
          attributeValue = if (1 == refs.getAllAsRefs().size) m_CurrentVerseLow.toStringOsis() else refs.toStringOsis()
        }


        "currentRefAsRefs" ->
        {
          val refs = RefRange(m_CurrentVerseLow, m_CurrentVerseHigh)
          attributeValue = ""; for (ref in refs.getAllAsRefs()) attributeValue += " " + ref.toStringOsis()
          attributeValue = attributeValue!!.substring(1)
        }


        "refCounter" ->
        {
          attributeValue = (++m_PerVerseReferenceCounter).toString()
        }

        else ->
        {
          attributeName = m.group("name")
          val optional = null != m.group("optional")

          if (attributeName.matches("(?i)as.*?ref_.*".toRegex())) // eg @AsOsisRef_loc
          {
            attributeValue = getRefBasedAttributeValue(attributeName)
          }
          else
          {
            attributeValue = Dom.getAttribute(node, attributeName)
            if (null == attributeValue && optional) attributeValue = ""
          }
        }
      }

      attributeValue = attributeValue!!.replace("&lt;", "<").replace("&gt;", ">")
      tags = m.replaceFirst(attributeValue!!)
    }

    return tags
  }


  /****************************************************************************/
  /* Haven't put a huge amount of effort into this: if we have the whole of the
     OT plus the whole of the NT, I return null (even if we also have DC books).
     Otherwise if we have the whole of either testament, I give the thing as a
     range.  Otherwise I name every book (including DC)
  
     Have to say I'm really not too sure about this.  The OSIS doc seems to
     give the impression that it's needed any time you don't have an entire
     work, but in the context of the Bible I don't know what an entire work
     would be anyway (ie what about DC texts?).  So I'm kinda doing something
     here (and assuming that the whole of the OT plus the whole of the OT does
     indeed constitute an entire work), but whether this is the right thing to
     do -- or whether it actually matters anyway -- I have no idea. */

  private fun makeScope(): String?
  {
    val mapper = BibleBookAndFileMapperEnhancedUsx
    if (mapper.hasFullOt() && mapper.hasFullNt()) return null
    var resOt = ""; if (mapper.hasFullOt()) resOt = "GEN-MAL" else if (mapper.hasOt()) resOt = java.lang.String.join(" ", mapper.getBooksOt())
    var resNt = ""; if (mapper.hasFullNt()) resNt = "MAT-REV" else if (mapper.hasNt()) resNt = java.lang.String.join(" ", mapper.getBooksNt())
    var resDc = ""; if (mapper.hasDc()) resDc = java.lang.String.join(" ", mapper.getBooksDc())
    var res = "$resOt $resNt $resDc"
    res = res.trim().replace("\\s+".toRegex(), " ")
    return res
  }


  /****************************************************************************/
  /* At most one tag at a time should be passed to this method, and this tag
     should be preceded by nothing other than (optional) whitespace. */

  private fun output (text: String)
  {
    var s = text
    if (m_SuppressOutput > 0) return
    val ss = s.trim().lowercase()
    if (m_JustOutputNewLine) while (s.startsWith("\n")) s = s.substring(1)
    s = s.replace("\n".toRegex(), "@NL@")
    s = s.replace("\\s+".toRegex(), " ")
    s = s.replace("@NL@".toRegex(), "\n")
    s = s.replace("\n+".toRegex(), "\n")
    s = s.replace("\\s*\n+\\s*".toRegex(), "\n")
    if (!ss.startsWith("<"))
    {
      s = s.replace("' \"", "'\"") // Single-quoted text within double-quoted often has a space between the terminating single quote and the terminating double quote if the two appear at the same place, and this confuses things, so ditch the space.
      if (s.contains("\"")) s = s.replace("(\\S+)\"".toRegex(), "$1”").replace("\"".toRegex(), "“")
      if (s.contains("'")) s = s.replace("(\\S+)'".toRegex(), "$1’").replace("'".toRegex(), "‘")
      s = s.replace("“\\s+".toRegex(), "“").replace("\\s+”".toRegex(), "”")
      s = s.replace("‘\\s+".toRegex(), "‘").replace("\\s+’".toRegex(), "’")
    }

    if (s.startsWith("<note")) SharedData.SpecialFeatures.setHasFootnotes(true)

    m_Out.write(s)
    m_JustOutputNewLine = s.endsWith("\n")
  }


  /****************************************************************************/
  /* Processes a single USX input file.  The file must be our STEP internal
     extended USX format.  It is the responsibility of the caller to join the
     outputs from the various files together and add a suitable wrapper,
     metadata, etc.
  */

  private fun processFile (bookName: String, inputFilePath: String, document: Document)
  {
    m_Document = document
    reportBookBeingProcessed(m_Document)
    Logger.setPrefix("TextConverterProcessorUsxToOsis (" + StepFileUtils.getFileName(inputFilePath).split(".")[0] + "): ")
    if (C_CollapseSubverses) collapseSubverses()
    convertVerseNodesToNewFormat()
    deleteVerseMarkersEmbeddedWithinCanonicalHeadings()
    processNode(m_Document.documentElement)
    Logger.setPrefix(null)
  }


  /****************************************************************************/
  /* We may have subverses at present - either because they were present in the
     raw USX or because reversification has created them.  At the time of
     writing, we have made a decision that they should be collapsed into the
     owning verse, which is handled by this present method.  This is controlled
     by a compile-time flag in case we ever change our minds and decide to
     retain the subverses. */

  private fun collapseSubverses ()
  {
    Dom.findNodesByName(m_Document, "_X_chapter").forEach { collapseSubverses(it) }
  }


  /****************************************************************************/
  private fun collapseSubverses (chapter: Node)
  {
    /**************************************************************************/
    //Dbg.d(chapter)
    //if (Dbg.dCont(Dom.toString(chapter), "13"))
    //  Dbg.outputDom(document)



    /**************************************************************************/
    val allVerses = Dom.findNodesByName(chapter, "verse", false)
    val (allSids, allEids) = allVerses.partition { Dom.hasAttribute(it, "sid") }
    val sidToEidMapping = allSids.zip(allEids).toMap()
    val sidGroups = allSids.groupBy { Ref.rd(Dom.getAttribute(it, "sid")!!).toRefKey_bcv() } // Group together all sids for the same verse.



    /**************************************************************************/
    fun handleVerseEnd (sidNode: Node)
    {
      val eidNode = sidToEidMapping[sidNode]!!
      val separator = Dom.createNode(chapter.ownerDocument, "<_X_subverseSeparator/>")
      Dom.insertNodeBefore(eidNode, separator)
      Dom.deleteNode(eidNode)
    }



    /**************************************************************************/
    fun processGroup (verseRefKey: RefKey, group: List<Node>)
    {
      val lastRefKey = Ref.rd(Dom.getAttribute(group.last(), "sid")!!).toRefKey()

      if (!Ref.hasS(lastRefKey)) return // If the group doesn't end with a subverse, it can't have any subverses at all.

      if (1 == group.size)
      {
        Logger.error("Verse consisting of just a single subverse: ${Ref.rd(verseRefKey)}")
        return
      }

      val firstRefKey = Ref.rd(Dom.getAttribute(group[0], "sid")!!).toRefKey()
      val coverage = Dom.getAttribute(group[0], "sid")!! + "-" + Dom.getAttribute(group.last(), "sid")!!
      group.subList(1, group.size).forEach { Dom.deleteNode(it) }
      Dom.setAttribute(group[0], "sid", Ref.rd(Ref.clearS(firstRefKey)).toString())
      Dom.setAttribute(group[0], "_X_subverseCoverage", coverage)

      group.subList(0, group.size - 1).forEach { handleVerseEnd(it) }
    }



    /**************************************************************************/
    sidGroups.forEach { processGroup(it.key, it.value) }
  }





  /****************************************************************************/
  /* It's convenient to distinguish verse sids and eids as different node
     types for later processing. */

  private fun convertVerseNodesToNewFormat()
  {
    val verses: List<Node> = Dom.findNodesByName(m_Document, "verse")
    verses.filter { Dom.hasAttribute(it, "sid") }.forEach { Dom.setNodeName(it, "_X_verseSid") }
    verses.filter { Dom.hasAttribute(it, "eid") }.forEach { Dom.setNodeName(it, "_X_verseEid") }
  }


  /****************************************************************************/
  /* In some texts (at the time of writing notably Hab 3 in NIV2011), we may
     have verse markers within canonical headings.  It seems that this is
     commonly handled eg on BibleGateway by _not_ retaining the verse marker
     within the heading, and then having the text start at v2 after the
     heading.  I therefore need to delete verse markers from within
     headings.

     Note that this will have knock-on effects: v1 will no longer be
     accessible, because effectively it will not exist. */

  private fun deleteVerseMarkersEmbeddedWithinCanonicalHeadings ()
  {
    fun process (heading: Node)
    {
      Dom.findNodesByName(heading, "verse", false).forEach { Dom.deleteNode(it) }
    }

    Dom.findNodesByAttributeValue(m_Document, "para", "style", "d").forEach { process(it) }
  }


  /****************************************************************************/
  private fun processNode (node: Node)
  {
    processNode(node, "")
    Dom.getChildren(node).forEach { processNode(it) }
    if ("#text" != Dom.getNodeName(node)) processNode(node, "/")
  }


  /****************************************************************************/
  private fun processNode (node: Node, closeMarker: String)
  {
    /**************************************************************************/
    val isClosing = "/" == closeMarker
    val lookupKey = closeMarker + getExtendedNodeName(node)
    val tagProcessDetails = ConfigData.getUsxToOsisTagTranslation(lookupKey)
    var tags: String? = tagProcessDetails?.first



    /**************************************************************************/
    if ("#text" == lookupKey)
    {
      var s = node.nodeValue
      s = s.replace("&(?!amp;)".toRegex(), "&amp;")
      output(s)
      return
    }

    when (lookupKey) {
      "_X_book" ->
      {
        m_ChapterNo = RefBase.C_DummyElement
        val usxBookAbbreviation: String = Dom.getAttribute(node, "code")!!
        m_UsxBookNumber = BibleBookNamesUsx.abbreviatedNameToNumber(usxBookAbbreviation)
        m_CurrentVerseLow = Ref.rd(m_UsxBookNumber, RefBase.C_DummyElement, RefBase.C_DummyElement, RefBase.C_DummyElement)
        m_CurrentVerseHigh = Ref.rd(m_UsxBookNumber, RefBase.C_DummyElement, RefBase.C_DummyElement, RefBase.C_DummyElement)
      }


      "_X_chapter" ->
      {
        m_ChapterNo = Ref.rdUsx(Dom.getAttribute(node, "sid")!!).getC()
        m_CurrentVerseLow = Ref.rd(m_UsxBookNumber, m_ChapterNo, RefBase.C_DummyElement, RefBase.C_DummyElement)
        m_CurrentVerseHigh = Ref.rd(m_UsxBookNumber, m_ChapterNo, RefBase.C_DummyElement, RefBase.C_DummyElement)
      }


      "_X_strong" ->
       {
        val strongsElts: MutableList<String> = Dom.getAttribute(node, "strong")!!.trim().split(",") as MutableList<String>
        var prevSelector = ""
        fun tidy (ix: Int)
        {
          var elt = strongsElts[ix].trim { it <= ' ' }
          if (!elt.startsWith("H") && !elt.startsWith("G")) elt = prevSelector + elt
          val m = C_Pattern_Strongs.matcher(elt)
          m.find()
          strongsElts[ix] = "strong:" + m.group("gOrH") + String.format("%04d", m.group("digits").toInt()) + m.group("letters")
          if (m.group("gOrH").isNotEmpty()) prevSelector = m.group("gOrH")
        }

        strongsElts.indices.forEach { tidy(it) }
        val strongs = java.lang.String.join(" ", strongsElts)
        //val lemma: String? = if (Dom.hasAttribute(node, "lemma")) Dom.getAttribute(node, "lemma")!!.trim() else null
        val morph: String? = if (Dom.hasAttribute(node, "morph")) Dom.getAttribute(node, "morph")!!.trim() else null // Wishful thinking: in fact I can't see anything anywhere at all in the USX 3 spec to suggest it supports morphology.
        val srcloc: String? = if (Dom.hasAttribute(node, "srcloc")) Dom.getAttribute(node, "srcloc")!!.trim() else null // Wishful thinking: in fact I can't see anything anywhere at all in the USX 3 spec to suggest it supports this.
        Dom.deleteAllAttributes(node)
        Dom.setAttribute(node, "lemma", strongs) // _Not_ 'gloss' as per OSIS documentation.
        //if (null != lemma)  Dom.setAttribute(node, "lemma",  lemma); Can't use lemma, because that holds the Strong's number.
        if (null != morph) Dom.setAttribute(node, "morph", morph)
        if (null != srcloc) Dom.setAttribute(node, "src", srcloc)
      }

      "_X_verseSid" ->
       {
        m_CurrentReferenceCollection = RefCollection(Ref.rdUsx(Dom.getAttribute(node, "sid")!!))
        m_CurrentVerseLow = Ref.rd(m_CurrentReferenceCollection.getLowAsRef())
        m_CurrentVerseHigh = Ref.rd(m_CurrentReferenceCollection.getHighAsRef())
        if (0 == m_CurrentVerseLow.getS()) m_CurrentVerseLow.setS(RefBase.C_DummyElement)
        if (0 == m_CurrentVerseHigh.getS()) m_CurrentVerseHigh.setS(RefBase.C_DummyElement)
        m_PerVerseReferenceCounter = 0
      }

      "cell" ->
      {
        if (null != tags) tags = tags.replace("start", "left").replace("end", "right")
      }
    }



    /**************************************************************************/
    if (null == tags)
    {
      Logger.error("Unknown tag type: " + Dom.toString(node))
      return
    }



    /**************************************************************************/
    tags = fillInAttributes(tags, node)



    /**************************************************************************/
    /* Set about doing the output.  Note that I assume here that where we are
       outputting multiple tags (multiple start tags or multiple end tags),
       processing elsewhere will have them in the right order, so we simply
       have to run through things in the correct order here.  One slight
       wrinkle, though -- I make sure here that each tag is output separately,
       and that any non-tag text is also output separately.  This reflects the
       fact that certainly at one stage it looked as though it might be useful
       to put certain checks into the output routine, and those were made
       simpler with this kind of split. */

    if (!isClosing && null != tagProcessDetails && ConfigData.TagAction.SuppressContent === tagProcessDetails.second) ++m_SuppressOutput

    if (tags.isNotEmpty())
    {
      val splitTags = tags.split("<".toRegex())
      output(splitTags[0]) // May have leading non-tag text.
      for (i in 1..< splitTags.size) output("<" + splitTags[i]) // This assumes no inter-tag text, but does permit text at the end.
    }

    if (isClosing && null != tagProcessDetails && ConfigData.TagAction.SuppressContent === tagProcessDetails.second) --m_SuppressOutput
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Validation                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* No longer used.  It's very slow for one thing, but also we know ahead of
     time that in many cases the OSIS we generate is non-compliant.  For
     instance, bullet-point lists and poetry are both supposed to be enclosed
     in the equivalent of HTML <ul>, but we don't do that, because things work
     without, and there's too much vertical whitespace on the screen if we
     include thm.
   */

  private fun validateXmlAgainstSchema (xmlPath: String): String?
  {
      return try
      {
        Dbg.reportProgress("\nReading XSD")
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        val schema = factory.newSchema(URL(getOsisXsdLocation()))
        val validator = schema.newValidator()
        Dbg.reportProgress("  Validating OSIS")
        validator.validate(StreamSource(File(xmlPath)))
        null
      }
      catch (e: Exception)
      {
        e.toString()
      }
    }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Local variables                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private lateinit var m_Document: Document


  /****************************************************************************/
  /* Current location details. */

  private var m_ChapterNo = 0
  private lateinit var m_CurrentReferenceCollection: RefCollection
  private lateinit var m_CurrentVerseLow: Ref
  private lateinit var m_CurrentVerseHigh: Ref
  private var m_UsxBookNumber = 0


  /****************************************************************************/
  /* Not really desperately useful -- simply used to avoid outputting loads of
     blank lines in the OSIS output, to make it look slightly better when viewed
     in a text editor. */

  private var m_JustOutputNewLine = false


  /****************************************************************************/
  /* Fairly obvious, I guess. */

  private lateinit var m_Out: BufferedWriter


  /****************************************************************************/
  /* There are certain tags which might in theory generate output, but where we
     wish to suppress all output from and including the starting tag up to and
     including the ending tag.  The following variable suppresses output when
     non-zero.  The idea is that you should increment it on the opening tag and
     decrement it on the closing tag where suppression is an issue. */

  private var m_SuppressOutput = 0


  /****************************************************************************/
  /* Used to ensure that all references within a single verse can be assigned
     unique ids. */

  private var m_PerVerseReferenceCounter = 0



  /****************************************************************************/
  /* Lets you identify attribute names appearing in the configuration data
    which defines how USX tags are converted to OSIS. */

  private val C_Pattern_AttributesInTagDetails = Pattern.compile("@(?<name>\\w+)(?<optional>\\??)")
  private val C_Pattern_Strongs = Pattern.compile("(?<gOrH>[GH])(?<digits>\\d+)(?<letters>[a-zA-Z]*)")
}
