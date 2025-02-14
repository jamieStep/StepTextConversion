/******************************************************************************/
package org.stepbible.textconverter.usxinputonly

import org.stepbible.textconverter.builders.BuilderUtils
import org.stepbible.textconverter.osisonly.Osis_Utils
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.w3c.dom.Node
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType


/******************************************************************************/
/**
 * A class which converts a collection of canonicalised USX data to OSIS.
 *
 * The USX-to-OSIS mappings are based mainly upon some suggestions for
 * USFM-to-OSIS mappings in the OSIS 2.1.1 reference manual, Appendix F, but
 * modified in the light of experience.
 *
 * @author ARA "Jamie" Jamieson
 */

object Usx_OsisCreator: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Package                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Note that this class _is_ parallelisable, but does not follow my common
     pattern of one main class and then a subclass which is instantiated for
     each book.
   */

  /****************************************************************************/
  /**
   * Converts a collection of USX documents to OSIS.
   */

  fun process (usxDataCollection: X_DataCollection)
  {
    Rpt.report(0, "Converting USX to OSIS")
    Rpt.reportWithContinuation(level = 1, "Processing ...") {
      process1(usxDataCollection)
    }
  }


  /****************************************************************************/
  private fun process1 (usxDataCollection: X_DataCollection)
  {
    val outputFilePath = BuilderUtils.createExternalOsisFolderStructure()
    val writer = File(outputFilePath).bufferedWriter()



    /**************************************************************************/
    val contents = getContents(usxDataCollection)



    /**************************************************************************/
    writer.write(Osis_Utils.fileHeader(usxDataCollection.getBookNumbers()))
    writer.write(contents)
    writer.write(Osis_Utils.fileTrailer())
    writer.close()



    /**************************************************************************/
    ExternalOsisDoc = Dom.getDocument(outputFilePath, retainComments = true)
    NodeMarker.deleteAllMarkers(ExternalOsisDoc)  // Make sure we didn't leave any temporary markers lying around.
  }


  /****************************************************************************/
  private class SingleBookThunk
  {
    fun getContent () = m_String.toString()
    fun write (s: String) { m_String.append(s) }
    var m_String = StringBuffer(20_000_000)


    /**************************************************************************/
    /* Current location details. */

    var m_ChapterNo = 0
    lateinit var m_CurrentReferenceCollection: RefCollection
    lateinit var m_CurrentVerseLow: Ref
    lateinit var m_CurrentVerseHigh: Ref
    var m_UsxBookNumber = 0


    /**************************************************************************/
    /* Not really desperately useful -- simply used to avoid outputting loads of
       blank lines in the OSIS output, to make it look slightly better when
       viewed in a text editor. */

    var m_JustOutputNewLine = false


    /**************************************************************************/
    /* There are certain tags which might in theory generate output, but where
       we wish to suppress all output from and including the starting tag up to
       and including the ending tag.  The following variable suppresses output
       when non-zero.  The idea is that you should increment it on the opening
       tag and decrement it on the closing tag where suppression is an issue. */

    var m_SuppressOutput = 0


    /****************************************************************************/
    /* Used to ensure that all references within a single verse can be assigned
       unique ids. */

    var m_PerVerseReferenceCounter = 0
  }


  /****************************************************************************/
  private fun getContents (usxDataCollection: X_DataCollection): String
  {
    /*************************************************************************/
    val chunks = ConcurrentHashMap<Int, String>()



    /*************************************************************************/
    /* Note that this does not follow my common pattern of having a separate
       instance of some subclass to handle each book.  Instead, I create
       storage separately for each book (SingleBookThunk), write to that,
       and then pass the result to a thread-safe ConcurrentHashMap. */

    with(ParallelRunning(true)) {
      run {
        usxDataCollection.getRootNodes().forEach { rootNode ->
          asyncable {
            val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(rootNode["code"]!!)
            val res = SingleBookThunk()
            processRootNode(res, rootNode)
            chunks[bookNo] = res.getContent()
          }
        } // forEach
      } // run
    } // with



    /*************************************************************************/
    return BookOrdering.getOrder().joinToString(""){ chunks[it] ?: "" }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun processRootNode (thunk: SingleBookThunk, rootNode: Node)
  {
    val bookName = rootNode["code"]!!
    Rpt.reportBookAsContinuation(bookName)
    processNode(thunk, rootNode)
  }


  /****************************************************************************/
  /* Called to handle any "@" variables in the tag translation details. */

  private fun fillInAttributes (thunk: SingleBookThunk, theTags: String, node: Node): String
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
          val ref: Ref = Ref.rd(thunk.m_CurrentVerseLow)
          if (!ref.hasC()) ref.setC(0)
          ref.clearV()
          ref.clearS()
          attributeValue = ref.toStringOsis()
          if (0 == ref.getC()) attributeValue += ".0" // Force chapter zero for use in introductions.
        }


        "currentChapterVerseZero" ->
        {
          val ref = Ref.rd(thunk.m_CurrentVerseLow)
          if (!ref.hasC()) ref.setC(0)
          ref.setV(0)
          ref.clearS()
          attributeValue = ref.toStringOsis() + ".0.0"
        }


        "currentRefLow" ->
        {
          attributeValue = thunk.m_CurrentVerseLow.toStringOsis()
        }


        "currentRefLow_b" ->
        {
          attributeValue = thunk.m_CurrentVerseLow.toStringOsis("b")
        }


        "currentRefLow_bc" ->
        {
          attributeValue = thunk.m_CurrentVerseLow.toStringOsis("bc")
        }


        "currentRefAsRange" ->
        {
          val refs = RefRange(thunk.m_CurrentVerseLow, thunk.m_CurrentVerseHigh)
          attributeValue = if (1 == refs.getAllAsRefs().size) thunk.m_CurrentVerseLow.toStringOsis() else refs.toStringOsis()
        }


        "currentRefAsRefs" ->
        {
          val refs = RefRange(thunk.m_CurrentVerseLow, thunk.m_CurrentVerseHigh)
          attributeValue = ""; for (ref in refs.getAllAsRefs()) attributeValue += " " + ref.toStringOsis()
          attributeValue = attributeValue!!.substring(1)
        }


        "refCounter" ->
        {
          attributeValue = (++thunk.m_PerVerseReferenceCounter).toString()
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
  /* At most one tag at a time should be passed to this method, and this tag
     should be preceded by nothing other than (optional) whitespace. */

  private fun output (thunk: SingleBookThunk, text: String)
  {
    var s = text
    if (thunk.m_SuppressOutput > 0) return
    val ss = s.trim().lowercase()
    if (thunk.m_JustOutputNewLine) while (s.startsWith("\n")) s = s.substring(1)
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

    thunk.write(s)
    thunk.m_JustOutputNewLine = s.endsWith("\n")
  }


  /****************************************************************************/
  private fun processNode (thunk: SingleBookThunk, node: Node)
  {
    processNode(thunk, node, "")
    Dom.getChildren(node).forEach { processNode(thunk, it) }
    if ("#text" != Dom.getNodeName(node)) processNode(thunk, node, "/")
  }


  /****************************************************************************/
  private fun processNode (thunk: SingleBookThunk, node: Node, closeMarker: String)
  {
    /**************************************************************************/
    val extendedName = Usx_FileProtocol.getExtendedNodeName(node)
    val isClosing = "/" == closeMarker
    val lookupKey = closeMarker + extendedName
    val tagProcessDetails = ConfigData.getUsxToOsisTagTranslation(lookupKey)
    var tags: String? = tagProcessDetails?.first



    /**************************************************************************/
    /* Regrettably, some USX semantics are lost in converting to OSIS, which
       lacks equivalent semantic tags.  It is therefore convenient -- if a bit
       of a pain -- to add some temporary attributes to the tags to record the
       USX tag details. */

    if (null != tags && "<" in tags && !tags.startsWith("</"))
    {
      val insert = " _t='y' _usx='$extendedName'"
      val x = tags.split("<").toMutableList()
      for (i in x.indices)
      {
        var ix = x[i].indexOf(" ")
        if (-1 == ix) ix = x[i].indexOf(">")
        if (-1 != ix) x[i] = x[i].substring(0, ix) + insert + x[i].substring(ix)
      }

      tags = x.joinToString("<")
    }


    /**************************************************************************/
    if ("#text" == lookupKey)
    {
      var s = node.nodeValue
      s = s.replace("&(?!amp;)".toRegex(), "&amp;")
      output(thunk, s)
      return
    }

    when (lookupKey) {
      "#comment" -> return


      "book" ->
      {
        thunk.m_ChapterNo = RefBase.C_DummyElement
        val usxBookAbbreviation: String = Dom.getAttribute(node, "code")!!
        thunk.m_UsxBookNumber = BibleBookNamesUsx.abbreviatedNameToNumber(usxBookAbbreviation)
        thunk.m_CurrentVerseLow = Ref.rd(thunk.m_UsxBookNumber, RefBase.C_DummyElement, RefBase.C_DummyElement, RefBase.C_DummyElement)
        thunk.m_CurrentVerseHigh = Ref.rd(thunk.m_UsxBookNumber, RefBase.C_DummyElement, RefBase.C_DummyElement, RefBase.C_DummyElement)
      }


      "chapter", "chapter:sid" ->
      {
        if ("sid" in node)
        {
          thunk.m_ChapterNo = Ref.rdUsx(Dom.getAttribute(node, "sid")!!).getC()
          thunk.m_CurrentVerseLow = Ref.rd(thunk.m_UsxBookNumber, thunk.m_ChapterNo, RefBase.C_DummyElement, RefBase.C_DummyElement)
          thunk.m_CurrentVerseHigh = Ref.rd(thunk.m_UsxBookNumber, thunk.m_ChapterNo, RefBase.C_DummyElement, RefBase.C_DummyElement)
        }
      }


//      "char:w" ->
//       {
//        val strongsElts = Dom.getAttribute(node, "strong")!!.trim().split(",")
//        val revisedStrongsElts: MutableList<String> = mutableListOf()
//        var prevSelector = ""
//        fun tidy (ix: Int)
//        {
//          var elt = strongsElts[ix].trim { it <= ' ' }
//          if (!elt.startsWith("H") && !elt.startsWith("G")) elt = prevSelector + elt
//          val m = C_Pattern_Strongs.matcher(elt)
//          m.find()
//          revisedStrongsElts.add("strong:" + m.group("gOrH") + String.format("%04d", m.group("digits").toInt()) + m.group("letters"))
//          if (m.group("gOrH").isNotEmpty()) prevSelector = m.group("gOrH")
//        }
//
//        strongsElts.indices.forEach { tidy(it) }
//        val strongs = java.lang.String.join(" ", revisedStrongsElts)
//        //val lemma: String? = if (Dom.hasAttribute(node, "lemma")) Dom.getAttribute(node, "lemma")!!.trim() else null
//        val morph: String? = if (Dom.hasAttribute(node, "morph")) Dom.getAttribute(node, "morph")!!.trim() else null // Wishful thinking: in fact I can't see anything anywhere at all in the USX 3 spec to suggest it supports morphology.
//        val srcloc: String? = if ("srcloc" in node) node["srcloc"]!!.trim() else null // Wishful thinking: in fact I can't see anything anywhere at all in the USX 3 spec to suggest it supports this.
//        Dom.deleteAllAttributes(node)
//        node["lemma"] = strongs // _Not_ 'gloss' as per OSIS documentation.
//        //if (null != lemma)  Dom.setAttribute(node, "lemma",  lemma); Can't use lemma, because that holds the Strong's number.
//        if (null != morph) node["morph"] = morph
//        if (null != srcloc) node["src"] = srcloc
//      }

      "verse", "verse:sid" ->
      {
        if ("sid" in node)
        {
          thunk.m_CurrentReferenceCollection = RefCollection.rdUsx(node["sid"]!!)
          thunk.m_CurrentVerseLow = Ref.rd(thunk.m_CurrentReferenceCollection.getLowAsRef())
          thunk.m_CurrentVerseHigh = Ref.rd(thunk.m_CurrentReferenceCollection.getHighAsRef())
          if (0 == thunk.m_CurrentVerseLow.getS()) thunk.m_CurrentVerseLow.setS(RefBase.C_DummyElement)
          if (0 == thunk.m_CurrentVerseHigh.getS()) thunk.m_CurrentVerseHigh.setS(RefBase.C_DummyElement)
          thunk.m_PerVerseReferenceCounter = 0
        }
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
    tags = fillInAttributes(thunk, tags, node)



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

    if (!isClosing && null != tagProcessDetails && ConfigData.TagAction.SuppressContent == tagProcessDetails.second) ++thunk.m_SuppressOutput

    if (tags.isNotEmpty())
    {
      val splitTags = tags.split("<".toRegex())
      output(thunk, splitTags[0]) // May have leading non-tag text.
      for (i in 1..< splitTags.size) output(thunk, "<" + splitTags[i]) // This assumes no inter-tag text, but does permit text at the end.
    }

    if (isClosing && null != tagProcessDetails && ConfigData.TagAction.SuppressContent == tagProcessDetails.second) --thunk.m_SuppressOutput
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

//  private fun validateXmlAgainstSchema (xmlPath: String): String?
//  {
//      return try
//      {
//        Rpt.report(level = 1, "\nReading XSD")
//        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
//        val schema = factory.newSchema(URL(ConfigData["stepExternalDataPath_OsisXsd"]!!)) // Version with Crosswire tweaks.
//        val validator = schema.newValidator()
//        Rpt.report(level = 1, "  Validating OSIS")
//        validator.validate(StreamSource(File(xmlPath)))
//        null
//      }
//      catch (e: Exception)
//      {
//        e.toString()
//      }
//    }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Local variables                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Lets you identify attribute names appearing in the configuration data
    which defines how USX tags are converted to OSIS. */

  private val C_Pattern_AttributesInTagDetails = Pattern.compile("@(?<name>\\w+)(?<optional>\\??)")
  private val C_Pattern_Strongs = Pattern.compile("(?<gOrH>[GH])(?<digits>\\d+)(?<letters>[a-zA-Z]*)")
}
