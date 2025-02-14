import org.stepbible.tyndaleStudyNotesEtc.Dbg
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.nio.file.Paths
import java.util.*


/******************************************************************************/
/**
 * Handles open access commentary- and dictionary- style material from
 * Tyndale Publishers (not related to Tyndale House).
 *
 *
 *
 *
 *
 * <h3>Material</h3>
 *
 * The material is split across a number of files in Tyndale's own dialect of
 * XML:
 *
 * - BookIntroSummaries.xml: Very brief per-book information, giving details
 *   of purpose, date, authorship, etc.
 *
 * - BookIntros.xml: More detailed per-book discussion.
 *
 * - Profiles.xml: Genealogical material for major characters.
 *
 * - StudyNotes.xml: Detailed study notes.
 *
 * - ThemeNotes.xml: Material arranged thematically.
 *
 *
 * Be aware that there were a number of minor errors and inconsistencies in
 * the material.  In some cases I have altered the source material manually.
 * In others, I have included corrective action in the processing here.  This
 * latter case covers, in particular, the fact that reference formats are
 * not entirely consistent, and also that a given book may be referred to by
 * different abbreviations in different places -- 1Sam vs ISam, for instance.
 *
 *
 *
 *
 *
 * <h3>Overall organisation</h3>
 *
 * The aim was specifically to turn StudyNotes.xml into OSIS and to create a
 * commentary module from that.  At the same time, we didn't want to lose
 * the information available in the other files.
 *
 * This has proved somewhat difficult.  The themes file is organised around
 * theme-names (marriage, baptism, etc) and not around verse references.  (It
 * does actually give a single verse reference for each topic, but I don't
 * think that's particular useful, because any meaty topic is going to be
 * based upon a number of different verses.  The same issues arise with the
 * genealogical data.
 *
 * Ideally both of these would be organised within STEPBible outside of the
 * normal book / chapter / verse structure, indexed by the name of the topic
 * or person involved.  Having done that they could be used as a standalone
 * resource, and we could also then 'properly' handle links to them from the
 * study notes.
 *
 * Unfortunately, we have yet to find a way of creating these as standalone
 * resources, and it's not entirely clear that we could link to them if we
 * did, so we're having to handle this by some slightly devious means.
 *
 * In summary therefore ...
 *
 * - The study notes themselves are organised around chapters and verses, which
 *   is fine -- or nearly so.  In quite a lot of cases, we have one note which
 *   is labelled with a verse _range_ and summarises the content of those verses
 *   as a whole, followed by separate notes labelled with each of the individual
 *   verses.  Unfortunately if you mirror this arrangement in the OSIS, the
 *   individual notes are suppressed.  To get round this I've had to pretend
 *   that the summary notes refer only to the first verse in their range.
 *   (I can still include something which shows them as belonging to the range;
 *   it's just that I have to give them a verse reference for the first verse
 *   in the range only.)
 *
 * - The book introductions and summaries I copy into the notes for the first
 *   verse of each book.
 *
 * - Any thematic or genealogical information is put into last notes for the
 *   chapter in which they appear.  (This almost certainly means that some
 *   information is duplicated from one place to another.)
 *
 *
 *
 *
 * <h3>Caveats and gotchas</h3>
 *
 * This work has uncovered a number of issues in ... well, in *something*:
 * it's not clear whether this is osis2mod, JSword, or something else entirely.
 *
 * - The study notes contain internal links from one place within the notes to
 *   another.  I have yet to find any OSIS facility which implements such links
 *   successfully, and am therefore leaving the link mechanism in place (in some
 *   cases the surrounding text requires something to be there), but replacing
 *   the actual link with an apology that the link doesn't work.
 *
 * - Where there are scripture links, ideally we would have preferred to use the
 *   scripture reference itself as the callout.  I've tried all sorts of things
 *   to make this work, but whatever you do, the callout comes out as a lower
 *   case letter.
 *
 * - The manner in which things are formatted on-screen depends upon what you
 *   have chosen to display at the time.  Looking at the notes standalone is
 *   different from looking at them alongside a Bible, and in this latter
 *   case interlinear and columnar display differ ...
 *
 * - In particular, you don't get to see verse numbers if you look at the
 *   text standalone -- you have to have a Bible open at the same time to
 *   get verse numbers.  And new paragraphs are not respected in interlinear
 *   mode (although in other modes they are).  Nor are line breaks (lb).
 *   Experimentation shows, however, that using poetry-line tags to introduce
 *   new lines seems to work satisfactorily in all modes.
 *
 * - The study notes text is written, in some instances, on the assumption that
 *   the text of a cross-reference is actually visible.  In OSIS terms, that
 *   would be like having:
 *
 *     ```
 *     If <note><reference>YOU CAN READ THIS</reference</note> I'll be amazed
 *     ```
 *
 *   Unfortunately, STEPBible *doesn't* display the enclosed text
 *   ('YOU CAN READ THIS'): the text only shows up when you hover over the
 *   callout and get the window which gives you details of the link.
 *
 * - Tables vanish in interlinear mode.  I had intended to use a table for the
 *   book summary material, but have had to abandon that idea.
 *
 *
 *
 *
 * <h3>Mechanics</h3>
 *
 * The thematic material and the profiles share enough in common that they can
 * be derived from a common class.  That class in turn shares enough in common
 * with the book intros and summaries that all of these can be derived from a
 * common class.  The one exception is the study notes, which require enough
 * special processing that it's not worth attempting to make them fir into the
 * overall structure.
 *
 * In general, the different input files do have their own collection of
 * paragraph tags, although the names have clearly been chosen to draw out
 * parallels between them, and I take advantage of this here to have a common
 * paragraph processor to handle all but those paragraph tags most specific to
 * an individual input file.  'span' tags are rather more common, so processing
 * for those is easily shared.
 *
 *
 *
 *
 * <h3>Useful information</h3>
 *
 * I never can remember how cross-references work, so this may be a good time
 * to record details of the experimentation I have carried out with this data.
 *
 * To create a functional cross-reference, all you actually *need* is a
 * reference tag:
 *
 *   ```
 *   <reference osisRef='Gen.1.1'>Some text to click on</reference>
 *   ```
 *
 * Here the osisRef attribute tells the internals where to point to, and must
 * be a complete reference in OSIS format -- either a single reference or a
 * range, but not a collection: if you want a collection you have to split it
 * out into separate reference tags.
 *
 * In fact we don't normally use this plain vanilla arrangement.  The enclosed
 * text ('Some text to click on' in the above example) appears simply as part
 * of the canonical text either side of it.  Occasionally, I suppose this may be
 * what you want (and indeed would have been perfectly acceptable in hte study
 * notes we are processing here), but even if you'd be happy to have it there, it
 * suffers from the drawback that when you click on it, the referenced text is
 * displayed in a new tab, which is seldom ideal.
 *
 * It is more normal to enclose the reference tag within a cross-reference note
 * tag:
 *
 *   ```
 *   <note n="Exod.32.1-Exod.32.4" osisID="Exod.32.1!n1" osisRef="Exod.32.1-Exod.32.4" type="crossReference">
 *     Exod 32:1-4
 *     <reference osisRef="DDDExod.32.1-Exod.32.4">Something to click on</reference>
 *   </note>
 *   ```
 *
 * So far as I can see, the sole effect of using the note tag is to display its
 * content in a pop-up window, rather than inline in the canonical text -- the
 * tag gives rise to a callout in the canonical text, and when you hover over
 * the callout, a pop-up appears which displays the content of the note tag.
 *
 * It is worth emphasising the fact that the pop-up displays *everything* which
 * appears in the note tag.  Typically you will arrange for its content to be
 * a reference tag -- and just a reference tag -- in which case you get to see
 * the content of the reference tag itself, as a hyperlink (prefixed, as it
 * happens, by the callout generated by the note tag).  But if you include, say
 * text within the note tag, you get that as well.
 *
 * What I don't understand is the purpose of the osisRef and osisId attributes
 * in the note tag, since they appear to do nothing -- it is the reference tag
 * which drives the link, not the note tag.
 *
 * As regards the precise details ...
 *
 * - According to the OSIS documentation, the 'n' attribute of the note tag
 *   is intended to let you control the text used as the callout.  To be fair,
 *   the documentation is slightly ambiguous as to whether this is true of the
 *   cross-reference note (there being several other flavours of notes), or
 *   whether cross-references are a special case.  Anyway, it doesn't:
 *   regardless of what you put there, you always get a lower case letter as the
  *  callout (starting at a, then running through b, c, ... z, aa, ab ...).
 *
 * - I *think* the osisRef and osisID attributes are required (the former
 *   associating a unique id with the note tag, and the latter recording
 *   where it points.  However, as mentioned above, I'm not sure anything
 *   actually does anything with them.  Certainly they don't seem to influence
  *  the rendered output or the actual cross-reference.
 *
 * - Within the context of the note tag, the reference tag is, as I say,
 *   displayed within a pop-up -- you get to see the content of the reference
 *   tag (the 'Something to click on' in the above example), preceded by a
 *   copy of the callout.
 *
 * - You can, if you wish, include other things -- typically text -- inside
 *   the note tag, in addition to the reference tag.  If you do that, the
 *   pop-up includes this extra text.
 *
 * - If the osisRef of the reference tag is invalid, the pop-ups will still
 *   look much as usual, but the text which you normally click on in order
 *   to reach the cross-referenced text wil not be clickable.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
fun main ()
{
  println("BookIntroSummaries"); BookIntroSummaries.process(FileLocations.BookIntroSummaries)
  println("BookIntros"); BookIntros.process(FileLocations.BookIntros)
  println("Profiles"); Profiles.process(FileLocations.Profiles)
  println("Themes"); ThemeNotes.process(FileLocations.Themes)
  println("StudyNotes"); StudyNotes.process()
}



/****************************************************************************/
data class SummaryThunk (val bookName: String, val firstRef: String, val node: Node)





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
abstract class CommonFormat
{
  /****************************************************************************/
  protected lateinit var m_Doc: Document
  protected val m_Save: MutableMap<String, SummaryThunk> = mutableMapOf()


  /****************************************************************************/
  abstract fun process (item: Node, firstRef: String, bookName: String, body: Node)


  /****************************************************************************/
  fun getSavedData (key: String) = m_Save[key]!!


  /****************************************************************************/
  fun process (fileLocationsGroup: FileLocations.FileLocationsGroup)
  {
    val text = canonicaliseInputText(File(fileLocationsGroup.inputFilePath()).bufferedReader().readText())
    m_Doc = Dom.getDocumentFromText(text)
    m_Doc.findNodesByName("item").forEach { item ->
      val itemChildren = Dom.getChildren(item)
      val firstRef = itemChildren.find { "refs" == Dom.getNodeName(it) }!!.textContent.split("-")[0]
      val bookName = itemChildren.find { "title" == Dom.getNodeName(it) }!!.textContent
      val body = itemChildren.find { "body" == Dom.getNodeName(it) }!!
      process(item, firstRef, bookName, body)
    }
  }
  
  
  /****************************************************************************/
  companion object
  {
    /**************************************************************************/
    fun canonicaliseInputText (text: String): String
    {
      var res = text.replace(". . .", "...")
                           .replace(" ", " ")
                           .replace("• ", "<X_LIST/>•\u00A0")
      m_BookAbbreviationCanonicalisationMappings.filter { it.key != it.value } .forEach { res = res.replace(it.key + ".", it.value + ".") }
      m_BookAbbreviationCanonicalisationMappingsSubsidiary.forEach { res = res.replace(it.key + ".", it.value + ".") }
      return res
    }


    /**************************************************************************/
    fun findNodeAmongstChildren (parent: Node, nodeName: String) = Dom.getChildren(parent).find { nodeName == it.nodeName } !!


    /**************************************************************************/
    fun makeHeaderSeparator (doc: Document) = Dom.createNode(doc, "<X_HEADER_SEPARATOR/>")


    /**************************************************************************/
    fun makeParaBreak (doc: Document, dbgValue: String?): Node
    {
      val paraNode = Dom.createNode(doc, "<l level='1'/>")
//      if (null != dbgValue) paraNode["X_DBG"] = dbgValue
      return paraNode
    }


    /**************************************************************************/
    /* List items are a bit of a nightmare.  In the raw text they start with a
       bullet point, but they are not enclosed in any way, and in this form
       they are difficult to format.  You can insert a blank line before them,

     */

    fun processListItems (doc: Document)
    {
      doc.findNodesByName("X_LIST").forEach { Dom.insertNodeBefore(it, makeParaBreak(doc, "456"))}
      Dom.deleteNodes(Dom.findNodesByName(doc, "X_LIST"))
    }


    /**************************************************************************/
    fun processPara (node: Node)
    {
      /************************************************************************/
      fun italiciseWithinPara (node: Node)
      {
        Dom.setNodeName(node, "hi")
        Dom.deleteAllAttributes(node)
        node["type"] = "italic"
        val para = makeParaBreak(node.ownerDocument, "2")
        Dom.insertNodeBefore(node, para)
        Dom.deleteNode(node)
        para.appendChild(node)
      }



      /************************************************************************/
      var classType = node["class"]!!
      classType = classType.substring(classType.indexOf('-') + 1)
      when (classType)
      {
        "body",
        "body-fl",
        "body-fl-sp",
        "overview" -> {
          Dom.deleteAllAttributes(node)
          Dom.setNodeName(node,"l"); node["level"] = "1"
        }

        "extract" ->
          italiciseWithinPara(node)

        "h1" -> { // Blank line, followed by contents of node in bold face.
          val doc = node.ownerDocument
          Dom.insertNodeBefore(node, makeParaBreak(doc, "3"))
          Dom.insertNodeBefore(node, makeParaBreak(doc, "3"))
          val bold = Dom.createNode(doc, "<hi type='bold'/>")
          val children = Dom.getChildren(node)
          Dom.deleteNodes(children)
          Dom.addChildren(bold, children)
          Dom.insertNodeBefore(node, bold)
          Dom.insertNodeAfter(node, makeParaBreak(doc, "4"))
          Dom.deleteNode(node)
        }

        "h2", "refs-title" -> { // Blank line, followed by contents of node in italic.
          val doc = node.ownerDocument
          Dom.insertNodeBefore(node, makeParaBreak(doc, "3"))
          Dom.insertNodeBefore(node, makeParaBreak(doc, "3"))
          val bold = Dom.createNode(doc, "<hi type='italic'/>")
          val children = Dom.getChildren(node)
          Dom.deleteNodes(children)
          Dom.addChildren(bold, children)
          Dom.insertNodeBefore(node, bold)
          Dom.insertNodeAfter(node, makeParaBreak(doc, "4"))
          Dom.deleteNode(node)
        }

        "h2-noPrecedingWhitespace" -> { // As h2, except no preceding whitespace.
          val doc = node.ownerDocument
          Dom.insertNodeBefore(node, makeParaBreak(doc, "3"))
//          Dom.insertNodeBefore(node, makeParaBreak(doc, "3", true))
          val bold = Dom.createNode(doc, "<hi type='italic'/>")
          val children = Dom.getChildren(node)
          Dom.deleteNodes(children)
          Dom.addChildren(bold, children)
          Dom.insertNodeBefore(node, bold)
          Dom.insertNodeAfter(node, makeParaBreak(doc, "4"))
          Dom.deleteNode(node)
        }


        "list",
        "list-sp" ->
          { Dom.setNodeName(node, "l"); Dom.deleteAllAttributes(node); node["level"] = "1" }

        "poetry-1-sp" ->
          { Dom.setNodeName(node, "l"); Dom.deleteAllAttributes(node); node["level"] = "1" }

        "poetry-2" ->
          { Dom.setNodeName(node, "l"); Dom.deleteAllAttributes(node); node["level"] = "2" }


        "refs" ->
          { Dom.setNodeName(node, "hi"); Dom.deleteAllAttributes(node); node["type"] = "normal "}
      }
    }


    /**************************************************************************/
    fun processParas (doc: Document)
    {
      doc.findNodesByName("p").forEach(::processPara)
    }


    /**************************************************************************/
    fun processSpans (doc: Document)
    {
      /************************************************************************/
      fun renameNode (node: Node, newName: String)
      {
        Dom.deleteAllAttributes(node)
        Dom.setNodeName(node, newName)
      }



      /************************************************************************/
      fun convertToHi (node: Node, typeValue: String)
      {
        renameNode(node, "hi")
        Dom.setAttribute(node, "type", typeValue)
      }



      /************************************************************************/
      /* Common span processing.  'hiMappings' deals with the common requirement
         to convert something to a particular flavour of OSIS hi tag. */

      val hiMappings: Map<String, String> = mapOf(
        "era" to "normal", // BC or AD.
        "sn-excerpt" to "italic", // span:sn-excerpt appears to be simply a quote from the relevant Bible text.
        "sn-excerpt-roman" to "italic",
        "ital" to "italic",
        "sc" to "small-caps",
        "sn-excerpt-sc" to "small-caps",
        "sub" to "sub",
        "sup" to "super",

        "aramaic" to "bold",
        "greek" to "bold",
        "hebrew" to "bold",
        "latin" to "bold",
        "sn-hebrew-chars" to "bold",
      )

      doc.findNodesByName("span").forEach {
        when (val classType = it["class"]!!)
        {
          "divineName", "sn-excerpt-divine-name" -> renameNode(it, "divineName")

          "divine-name-italic" ->
          {
            renameNode(it, "divineName")
            val italicNode = Dom.createNode(doc, "<hi type='italic'/>")
            Dom.insertNodeBefore(it, italicNode)
            Dom.deleteNode(it)
            italicNode.appendChild(it)
          }

          "ital-bold" ->
          {
            convertToHi(it, "italic")
            val boldNode = Dom.createNode(doc, "<hi type='bold'/>")
            Dom.insertNodeBefore(it, boldNode)
            Dom.deleteNode(it)
            boldNode.appendChild(it)
          }

          "sn-list-1", "sn-list-2", "sn-list-3" ->
          {
            val n = classType.last().digitToInt()
            Dom.deleteAllAttributes(it)
            Dom.insertAsFirstChild(it, Dom.createTextNode(doc, "\u00a0".repeat(2 * n)))
          }

          "sn-ref" -> // So far as I can see this encapsulates the scripture reference to which the note applies.  We want to retain the cross reference.
          {
            Dom.insertNodesBefore(it, Dom.getChildren(it))
            Dom.deleteNode(it)
          }

          else ->
          {
            val mapping = hiMappings[classType]
            if (null != mapping)
              convertToHi(it, mapping)
          }
        } // when
      } // doc.findNodesByName("span").forEach
    } // processSpans


    /***************************************************************************/
    /* Order is important below.  Books like IKgs and IIKgs need to have the
       longer name first. */

    private var m_BookAbbreviationCanonicalisationMappings = mapOf (
      "Gen" to "Gen",
      "Exod" to "Exod",
      "Lev" to "Lev",
      "Num" to "Num",
      "Deut" to "Deut",
      "Josh" to "Josh",
      "Judg" to "Judg",
      "Ruth" to "Ruth",
      "IISam" to "2Sam",
      "ISam" to "1Sam",
      "IIKgs" to "2Kgs",
      "IKgs" to "1Kgs",
      "IIChr" to "2Chr",
      "IChr" to "1Chr",
      "Ezra" to "Ezra",
      "Neh" to "Neh",
      "Esth" to "Esth",
      "Job" to "Job",
      "Ps" to "Ps",
      "Pr" to "Prov",
      "Eccl" to "Eccl",
      "Song" to "Song",
      "Isa" to "Isa",
      "Jer" to "Jer",
      "Lam" to "Lam",
      "Ezek" to "Ezek",
      "Dan" to "Dan",
      "Hos" to "Hos",
      "Joel" to "Joel",
      "Amos" to "Amos",
      "Obad" to "Obad",
      "Jon" to "Jonah",
      "Mic" to "Mic",
      "Nah" to "Nah",
      "Hab" to "Hab",
      "Zeph" to "Zeph",
      "Hagg" to "Hag",
      "Zech" to "Zech",
      "Mal" to "Mal",
      "Matt" to "Matt",
      "Mark" to "Mark",
      "Luke" to "Luke",
      "John" to "John",
      "Acts" to "Acts",
      "Rom" to "Rom",
      "IICor" to "2Cor",
      "ICor" to "1Cor",
      "Gal" to "Gal",
      "Eph" to "Eph",
      "Phil" to "Phil",
      "Col" to "Col",
      "IIThes" to "2Thess",
      "IThes" to "1Thess",
      "IITim" to "2Tim",
      "ITim" to "1Tim",
      "Titus" to "Titus",
      "Phlm" to "Phlm",
      "Heb" to "Heb",
      "Jas" to "Jas",
      "IIPet" to "2Pet",
      "IPet" to "1Pet",
      "IIIJn" to "3John",
      "IIJn" to "2John",
      "IJn" to "1John",
      "Jude" to "Jude",
      "Rev" to "Rev",
    )

    private var m_BookAbbreviationCanonicalisationMappingsSubsidiary = mapOf (
      "1Thes" to "1Thess",
      "2Thes" to "2Thess",
      "1Jn" to "1John",
      "2Jn" to "2John",
      "3Jn" to "3John"
    )
  }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* The summary information is extremely brief.  I need to do a fair bit of
   special processing here, because we need a very specific format for the
   data.  This means that the extent to which I can make use of common
   processing is limited.

   According to the _classes.txt file, the following tags are used (and therefore
   need to be handled.  (Experience suggests that in at least some cases, it is not
   safe to assume the list is correct.)
  
     p.intro-body Normal body text within a book introduction
     p.intro-body-fl Same as intro-body but usually set flush left
     p.intro-body-fl-sp Same as intro-body-fl but with space above
     p.intro-extract Extract text within a book introduction
     p.intro-h1 Top level subhead within a book introduction
     p.intro-list List paragraph within a book introduction
     p.intro-list-sp Same as intro-list but with space above
     p.intro-overview Overview paragraph within a book introduction
     p.intro-poetry-1-sp First level poetry indent with space above within a book introduction
     p.intro-poetry-2 Second level poetry indent within a book introduction
     p.intro-sidebar-body-fl Body text, set flush left within a book introduction sidebar
     p.intro-sidebar-h1 Top level subhead within a book introduction sidebar
     p.intro-title Title paragraph within a book introduction
     span.intro-h2 Second level heading text set inline within first line of text paragraph
     span.intro-h2-sc Small caps text within book introduction h2
     span.intro-h2-era Marks BC/AD era designations within a book introduction h2
*/

object BookIntros: CommonFormat ()
{
  /****************************************************************************/
  private var m_Ix = 0


  /****************************************************************************/
  override fun process (item: Node, firstRef: String, bookName: String, body: Node)
  {
    var children = Dom.getChildren(body)
    children.filter { Dom.isTextNode(it) } .forEach(Dom::deleteNode)
    children = Dom.getChildren(body)

    Dom.deleteNode(children[0]) // The title node.
    children = children.subList(1, children.size)

    children.forEach { it.getAllNodesBelow().forEach(::processSpans) }

    val outlineSummaryNode = Dom.createNode(m_Doc, "<X_CONTAINER/>") // The outline is going in a temporary collector node.

    val bold = Dom.createNode(m_Doc, "<hi type='bold'/>") // It contains a boldface title and we want to force a newline at the end of it.
    outlineSummaryNode.appendChild(bold)
    bold.appendChild(makeParaBreak(m_Doc, "1"))
    bold.appendChild(Dom.createTextNode(m_Doc,"Book of $bookName \u2014 Overview:"))
    outlineSummaryNode.appendChild(body) // Now we want the actual textual content followed by another para break.

    m_Save[(m_Ix++).toString()] = SummaryThunk(bookName, firstRef, outlineSummaryNode)
  }


  /******************************************************************************/
  fun processSpans (node: Node)
  {
    /**************************************************************************/
    if ("span" != Dom.getNodeName(node)) return



    /**************************************************************************/
    fun encloseIn (node: Node, vararg types: String)
    {
      Dom.deleteAllAttributes(node)
      Dom.setNodeName(node, types[0])
      val content = Dom.getChildren(node)
      Dom.deleteNodes(content)
      Dom.deleteChildren(node)

      var parent = node
      var bottomNode: Node? = null
      for (i in 1 ..< types.size)
      {
        bottomNode = Dom.createNode(node.ownerDocument, "<hi type='${types[i]}'/>")
        parent.appendChild(bottomNode)
        parent = bottomNode
      }

      Dom.addChildren(bottomNode!!, content)
    }


    /**********************************************************************/
    when (node["class"]!!)
    {
      "intro-h2" -> encloseIn(node, "bold", "italic")
      "intro-h2-era" -> encloseIn(node, "bold", "italic", "small-caps")
    }
  }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/* The summaries are very brief, as the example below demonstrates:

    <item name="GenesisIntroSummary" typename="BookIntroSummary" product="TyndaleOpenStudyNotes">
    <title>Genesis</title>
    <refs>Gen.1.1-50.26</refs>
    <body>
    <p class="intro-title">The Book of Genesis</p>
    <p class="intro-sidebar-h1">Purpose</p>
    <p class="intro-sidebar-body-fl">To trace the establishment of God’s plan to overcome sin through his chosen people, Israel</p>
    <p class="intro-sidebar-h1">Author</p>
    <p class="intro-sidebar-body-fl">Moses, according to tradition</p>
    <p class="intro-sidebar-h1">Date</p>
    <p class="intro-sidebar-body-fl">Records primeval events and events from the patriarchal period (ca. 2100–1700 <span class="era">BC</span>)</p>
    <p class="intro-sidebar-h1">Setting</p>
    <p class="intro-sidebar-body-fl">A variety of places in the Middle East, focusing heavily on the patriarchs during their time in Canaan and Egypt</p>
    </body>


  Originally I was intending to covert this to a table, something like:

    <table type='x-simpleTable'>
    <row type='x-simpleTable-row'><cell type='x-simpleTable-cell-left'>Purpose</cell><cell type='x-simpleTable-cell-left'>To trace the establishment of God’s plan to overcome sin through his chosen people, Israel</cell></row>
    <row type='x-simpleTable-row'><cell type='x-simpleTable-cell-left'>Author</cell><cell type='x-simpleTable-cell-left'>Moses, according to tradition</cell></row>
    <row type='x-simpleTable-row'><cell type='x-simpleTable-cell-left'>Date</cell><cell type='x-simpleTable-cell-left'>Records primeval events and events from the patriarchal period (ca. 2100–1700 <span class="era">BC</span>)</cell></row>
    <row type='x-simpleTable-row'><cell type='x-simpleTable-cell-left'>Setting</cell><cell type='x-simpleTable-cell-left'>A variety of places in the Middle East, focusing heavily on the patriarchs during their time in Canaan and Egypt</cell></row>
    </table>

  but possibly with enhanced line spacing and title, and then stored for use
  later.

  However, although tables look really nice in standalone and columnar mode,
  they vanish in interlinear mode, so I've had to use simple bold headings
  instead.
*/

object BookIntroSummaries: CommonFormat()
{
  /****************************************************************************/
  private var m_Ix = 0


  /****************************************************************************/
  override fun process (item: Node, firstRef: String, bookName: String, body: Node)
  {
    val content = processAsText(body)
    val outlineSummaryNode = Dom.createNode(m_Doc, "<X_CONTAINER/>") // The outline is going in its temporary collector node.
    val bold = Dom.createNode(m_Doc, "<hi type='bold'/>") // It contains a boldface title and we want to force a newline at the end of it.
    outlineSummaryNode.appendChild(bold)
    bold.appendChild(Dom.createTextNode(m_Doc,"Book of $bookName \u2014 Quick facts:"))
    bold.appendChild(makeParaBreak(m_Doc, "567"))
    outlineSummaryNode.appendChild(makeParaBreak(m_Doc, "5"))
    outlineSummaryNode.appendChild(content) // Now we want the actual textual content followed by another para break.
    outlineSummaryNode.appendChild(makeParaBreak(m_Doc, "6"))
    m_Save[(m_Ix++).toString()] = SummaryThunk(bookName, firstRef, outlineSummaryNode)
  }


  /****************************************************************************/
  private fun processAsTable (body: Node): Node
  {
    Dom.setNodeName(body, "table")
    body["type"] = "x-simple-table"

    var children = Dom.getChildren(body)
    children.filter { Dom.isTextNode(it) } .forEach(Dom::deleteNode)
    children = Dom.getChildren(body)

    Dom.deleteNode(children[0]) // The title node.
    children = children.subList(1, children.size)

    children.forEach {
      Dom.setNodeName(it, "cell")
      Dom.deleteAllAttributes(it)
      it["type"] = "x-simpleTable-cell-left"
    }

    for (i in children.indices step 2)
    {
      val n = Dom.createNode(m_Doc, "<row type='x-simpleTable-row'/>")
      Dom.insertNodeBefore(children[i], n)
      Dom.deleteNode(children[i])
      Dom.deleteNode(children[i + 1])
      n.appendChild(children[i])
      n.appendChild(children[i + 1])
    }

    return body
  }


  /****************************************************************************/
  private fun processAsText (body: Node): Node
  {

    Dom.setNodeName(body, "X_CONTAINER")

    var children = Dom.getChildren(body)
    children.filter { Dom.isTextNode(it) } .forEach(Dom::deleteNode)
    children = Dom.getChildren(body)

    Dom.deleteNode(children[0]) // The title node.
    children = children.subList(1, children.size)

    for (i in children.indices step 2)
    {
      val n = Dom.createNode(m_Doc, "<hi type='bold'/>")
      Dom.insertAsFirstChild(n, Dom.createTextNode(m_Doc, children[i].textContent + ": "))
      Dom.insertNodeAfter(children[i], n)
      Dom.deleteNode(children[i])
      Dom.insertAsLastChild(children[i + 1], makeParaBreak(m_Doc, "123"))
      Dom.insertAsLastChild(children[i + 1], makeParaBreak(m_Doc, null))
      Dom.promoteChildren(children[i + 1])
      Dom.deleteNode(children[i + 1])
    }

    return body
  }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
object StudyNotes
{
  /****************************************************************************/
  fun process ()
  {
    /**************************************************************************/
    val fileLocations = FileLocations.StudyNotes
    FileLocations.moduleName = "TNotes"
    val text = CommonFormat.canonicaliseInputText(File(fileLocations.inputFilePath()).bufferedReader().readText())
    val doc = Dom.getDocumentFromText(CommonFormat.canonicaliseInputText(text))



    /**************************************************************************/
    collectExternalReferences(doc)
    convertToOsis(doc)
    Dom.outputDomAsXml(doc, fileLocations.osisFilePath(), null)
    ExternalInterface.handleOsis2modCall(fileLocations)
    ExternalInterface.createModuleZip(fileLocations)
  }


  /****************************************************************************/
  /* Add in any summary information obtained from elsewhere.  We add this into
     the first element for a given book if possible (ie if the first element is
     already for chapter 1 verse 1).  Failing that, we have to create a new
     element to hold it. */

  private fun addExternalInformation (doc: Document, summaryGetter: (key: String) -> SummaryThunk)
  {
    var ix = -1
    var prevBookName = ""
    val items = doc.findNodesByName("item")
    items.forEach {
      val ref = it["name"]!!
      val bookName = ref.split(".")[0]
      if (bookName == prevBookName)
        return@forEach

      prevBookName = bookName

      val summaryThunk = summaryGetter((++ix).toString())
      if (ref.startsWith(summaryThunk.firstRef)) // We already have an entry for 1:1, so we can just insert this along with it.
      {
        val collectedData = Dom.cloneNode(doc, summaryThunk.node)
        Dom.insertAsFirstChild(it, collectedData)
        Dom.promoteChildren(collectedData)
        Dom.deleteNode(collectedData)
        return@forEach
      }

      TODO()
//      val marker = Dom.createNode(doc, "<X_MARKER ix='$ix'/>")
//      Dom.insertNodeBefore(it, marker)
    }
  }


  /****************************************************************************/
  private fun collapseWhitespace (doc: Document)
  {
    var justHadWhiteSpace = false
    doc.getAllNodesBelow().forEach {
      if (Dom.isWhitespace(it))
      {
        if (justHadWhiteSpace)
          Dom.deleteNode(it)
        else
          justHadWhiteSpace = true
      }
      else
        justHadWhiteSpace = false
    }
  }


  /****************************************************************************/
  private val m_LastItemInChapter: MutableMap<String, Node> = mutableMapOf()
  private val m_ProfileReferences: MutableMap<String, String> = mutableMapOf()
  private val m_ThemeReferences: MutableMap<String, String> = mutableMapOf()


  /****************************************************************************/
  private fun collectExternalReferences (doc: Document)
  {
    /**************************************************************************/
    doc.findNodesByName("a")
      .filter { val href = it["href"]!!; "_Filament" in href&& "Study" !in href  }
      .forEach {
        val href = it["href"]!!.replace("?item=", "")
        val target = href.split("_")[0]
        val item = Dom.findAncestorByNodeName(it, "item")!!
        val nameElts = item["name"]!!.split(".")
        val referenceFromChapter = nameElts[0] + "." + nameElts[1]
        if ("_Theme" in href)
          m_ThemeReferences[referenceFromChapter] = (m_ThemeReferences[referenceFromChapter] ?: "") + "," + target
        else if ("_Profile" in href)
          m_ProfileReferences[referenceFromChapter] = (m_ProfileReferences[referenceFromChapter] ?: "") + "," + target
        else if ("_BookIntro" in href)
          ;
        else
          TODO()
      }



    /**************************************************************************/
    doc.findNodesByName("item").forEach { item ->
      val nameElts = item["name"]!!.split("-")[0].split(".")
      val bc = nameElts[0] + "." + nameElts[1]
      m_LastItemInChapter[bc] = item
    }


    /**************************************************************************/
    m_ThemeReferences.forEach {
      it.value.substring(1).split(",").forEach { themeName ->
        val targetNode = m_LastItemInChapter[it.key]!!.findNodeByAttributeValue("p", "class", "sn-text")!!
        targetNode.appendChild(Dom.cloneNode(doc, ThemeNotes.getSavedData(themeName).node))
      }
    }


    /**************************************************************************/
    m_ProfileReferences.forEach {
      it.value.substring(1).split(",").forEach { profileName ->
        val targetNode = m_LastItemInChapter[it.key]!!.findNodeByAttributeValue("p", "class", "sn-text")!!
        targetNode.appendChild(Dom.cloneNode(doc, Profiles.getSavedData(profileName).node))
      }
    }
  }


  /****************************************************************************/
  private fun makeOsisHeader (doc: Document)
  {
    /**************************************************************************/
    val languageCode2CharIfPoss = "en"
    val osisIdWork = FileLocations.moduleName
    val nameOfWork = "Tyndale Open Study Notes"



    /**************************************************************************/
    val baseNode = doc.findNodeByName("items")!!
    Dom.deleteAllAttributes(baseNode)
    Dom.setNodeName(baseNode,"osis")
    Dom.setAttribute(baseNode, "xmlns:xsi",  "http://www.w3.org/2001/XMLSchema-instance")
    Dom.setAttribute(baseNode, "xmlns:osis", "http://www.bibletechnologies.net/2003/OSIS/namespace")
    Dom.setAttribute(baseNode, "xsi:schemaLocation", "http://www.bibletechnologies.net/2003/OSIS/namespace http://www.bibletechnologies.net/osisCore.2.1.1.xsd")
    val wrapper = Dom.createNode(doc,"<osisText osisIDWork='$osisIdWork' osisRefWork='Commentary' xml:lang='$languageCode2CharIfPoss' canonical='false'/>")
    Dom.addChildren(wrapper, Dom.getChildren(baseNode))
    Dom.deleteChildren(baseNode)
    baseNode.appendChild(wrapper)

    val header = Dom.createNode(doc, "<header/>")
    Dom.insertAsFirstChild(wrapper, header)
    val headerWork = Dom.createNode(doc, "<work osisWork='$osisIdWork'/>")
    header.appendChild(headerWork)

    val title = Dom.createNode(doc, "<title/>")
    headerWork.appendChild(title)
    val titleContent = Dom.createTextNode(doc, nameOfWork)
    title.appendChild(titleContent)

    val type = Dom.createNode(doc, "<type type='OSIS'/>")
    headerWork.appendChild(type)
    val typeContent = Dom.createTextNode(doc, "Commentary")
    type.appendChild(typeContent)

    val identifier = Dom.createNode(doc, "<identifier type='OSIS'/>")
    headerWork.appendChild(identifier)
    val identifierContent = Dom.createTextNode(doc, "Commentary.en.TH.StudyNotes.2024")
    identifier.appendChild(identifierContent)

    val rights = Dom.createNode(doc, "<rights type='x-openAccess'/>")
    headerWork.appendChild(rights)
    val rightsContent = Dom.createTextNode(doc, "Tyndale House")
    rights.appendChild(rightsContent)

    val refSystem = Dom.createNode(doc, "<refSystem/>")
    headerWork.appendChild(refSystem)
    val refSystemContent = Dom.createTextNode(doc, "Bible")
    refSystem.appendChild(refSystemContent)
  }


  /****************************************************************************/
  private fun processCrossReferences (doc: Document)
  {
    /**************************************************************************/
    var uniqueNumber = 0



    /**************************************************************************/
    fun createNoteNodes (noteNode: Node, formattedRef: String, precedeWithUserVisibleText: String, forceBoldFace: Boolean)
    {
      Dom.setNodeName(noteNode,"note")
      Dom.deleteAllAttributes(noteNode)
      Dom.setAttribute(noteNode, "osisRef", formattedRef)
      Dom.setAttribute(noteNode, "osisID", formattedRef.split("-")[0] + "!n${++uniqueNumber}")
      //Dom.setAttribute(noteNode, "n", formattedRef) // $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$
      Dom.setAttribute(noteNode, "n", "\u25BA") // Right arrow.
      Dom.setAttribute(noteNode, "type", "crossReference")

      val referenceNode = Dom.createNode(doc, "<reference osisRef='$formattedRef'/>")
      noteNode.appendChild(referenceNode)
      referenceNode.appendChild(Dom.createTextNode(doc, "\u25BA")) // Right arrow.

      if  (forceBoldFace)
      {
        val boldVerseNumberNode = Dom.createNode(doc, "<hi type='bold'/>")
        boldVerseNumberNode.appendChild(Dom.createTextNode(doc, precedeWithUserVisibleText +"\u00A0"))
        Dom.insertNodeBefore(noteNode, boldVerseNumberNode)
      }
      else
        Dom.insertNodeBefore(noteNode, Dom.createTextNode(doc, precedeWithUserVisibleText +"\u00A0"))
    }



    /**************************************************************************/
    fun processBibleReference (aNode: Node, href: String, clickableText: String, forceBoldFace: Boolean)
    {
      val hrefModified = href.replace("?bref=", "")
      createNoteNodes(aNode, ReferenceFormatter.restructureReference(hrefModified), precedeWithUserVisibleText = clickableText, forceBoldFace) // if (suppressVisibleRef) null else clickableText)
    }



    /**************************************************************************/
    /* This typically says something like 'See introduction to Genesis'.
       Promoting the text itself may be useful (assuming I can manage to
       include the relevant introductory material), but at present I'm not
       aware of any way of linking to it. */

    fun processBookIntroReference (aNode: Node, href: String, clickableText: String)
    {
      Dom.promoteChildren(aNode)
      Dom.deleteNode(aNode)
    }



    /**************************************************************************/
    fun processProfileReference (aNode: Node, href: String, clickableText: String)
    {
      val prevTextNode = aNode.previousSibling
      val prevNodeTextContent = prevTextNode.textContent
      if (!prevNodeTextContent.endsWith("“"))
        TODO()

      prevTextNode.textContent = prevNodeTextContent.substring(0, prevNodeTextContent.length - 1) + "profile for "

      val nextTextNode = aNode.nextSibling
      val nextNodeTextContent = nextTextNode.textContent
      if (!nextNodeTextContent.startsWith("” Profile"))
        TODO()

      nextTextNode.textContent = " at end of chapter" + nextNodeTextContent.substring("” Profile".length)

      Dom.deleteAllAttributes(aNode)
      Dom.setNodeName(aNode, "hi")
      aNode["type"] = "italic"
    }



    /**************************************************************************/
    /* Self-references appear to be formatted in the raw text as

         see <a href="?item=Gen.2.19-20_StudyNote_Filament">study note on 2:19-20</a>

       I can't find any way of implementing an actual link, but we can at least
       remove the non-working <a> node and retain the text, which will tell the
       user where to look. */

    fun processSelfReference (aNode: Node, href: String)
    {
      val content = aNode.textContent
      Dom.insertNodeBefore(aNode, Dom.createTextNode(doc, content))
      Dom.deleteNode(aNode)
    }



    /**************************************************************************/
    fun processThemeReference (aNode: Node, href: String, clickableText: String)
    {
      val prevTextNode = aNode.previousSibling
      val prevNodeTextContent = prevTextNode.textContent
      if (!prevNodeTextContent.endsWith("“"))
        TODO()

      prevTextNode.textContent = prevNodeTextContent.substring(0, prevNodeTextContent.length - 1) + "thematic note for "

      val nextTextNode = aNode.nextSibling
      val nextNodeTextContent = nextTextNode.textContent
      if (!nextNodeTextContent.startsWith("” Theme Note"))
        TODO()

      nextTextNode.textContent = " at end of chapter" + nextNodeTextContent.substring("” Theme Note".length)

      Dom.deleteAllAttributes(aNode)
      Dom.setNodeName(aNode, "hi")
      aNode["type"] = "italic"
    }



    /**************************************************************************/
    doc.findNodesByName("a").forEach { aNode ->
      val href = Dom.getAttribute(aNode, "href")!!
      val clickableText = aNode.textContent

      if ("?bref" in href)
        processBibleReference(aNode, href, clickableText, "X_FORCE_BOLDFACE" in aNode)
      else if ("?item" in href)
      {
        if ("BookIntro" in href) // BookIntro_Filament in StudyNotes; various formats in BookIntros.
          processBookIntroReference(aNode, href, clickableText)
        else if ("Profile_Filament" in href)
          processProfileReference(aNode, href, clickableText)
        else if ("StudyNote_Filament" in href)
          processSelfReference(aNode, href)
        else if ("ThemeNote_Filament" in href)
          processThemeReference(aNode, href, clickableText)
        else
          TODO()
      }
      else
        TODO()
    }
  }


  /****************************************************************************/
  private fun processVerticalWhitespaceBeforeHeaders (doc: Document)
  {
    doc.findNodesByName("X_HEADER_SEPARATOR").forEach separatorLoop@ { separator ->
      val item = Dom.findAncestorByNodeName(separator, "item")!!
      val descendants = Dom.getAllNodesBelow(item)
      descendants.forEach {
        if (it === separator)
        {
          Dom.deleteNode(separator)
          return@separatorLoop
        }

        if (Dom.isTextNode(it) && !Dom.isWhitespace(it))
        {
          Dom.insertNodeBefore(separator, CommonFormat.makeParaBreak(doc, "100"))
          Dom.insertNodeBefore(separator, CommonFormat.makeParaBreak(doc, null))
          Dom.deleteNode(separator)
          return@separatorLoop
        }

      }
    }

    val remainingHeaders = doc.findNodesByName( "X_HEADER_SEPARATOR")
    remainingHeaders.forEach(Dom::deleteNode)
  }


  /****************************************************************************/
  /* We have a problem with the items.  The data commonly has a sort of
     summary note -- 'Gen.1.1-10 covers the following topics', followed by
     individual notes for each of the verses.  If I retain this as such (and
     assuming my processing is correct), something somewhere suppresses all
     of the individual notes.

     I don't want to lose the text altogether, so instead I extract it and
     then insert it at an appropriate location, where 'appropriate' means ...

     - If there is an individual verse which matches the start of the summary
       range, I add the summary to the content for that verse, along with
       some appropriate headings.

     - Otherwise I create an individual verse entry to hold the data.

     At the end of this:

     - refs tags and body tags will have been removed or renamed.

     - item tags will remain (item tags being enclosing tags).


     body tags will have been removed.  Other things may
     have been moved around, and temporary nodes and attributes may have been
     added.  However, the important point is that the enclosing tags will
     still remain. */

  private fun processItemsPart1 (doc: Document)
  {
    /**************************************************************************/
    var pendingRefLowAsRefKey = -1
    var pendingRefAsString = ""
    var pendingSummaryItemTag: Node? = null
    var pendingSummary: Node? = null



//    /**************************************************************************/
//    /* Investigations. */
//
//    val x: MutableMap<String, Int> = mutableMapOf()
//    doc.findNodesByName("item").forEach { item ->
//      val aNodes = item.getAllNodesBelow().filter { "a" == it.nodeName }
//      val aNodesOfInterest = aNodes.count { aNode -> "Filament" in aNode["href"]!! && "StudyNote" !in aNode["href"]!! }
//      val book = item["name"]!!.split(".")[0]
//      x[book] = if (book in x) x[book]!! + aNodesOfInterest else aNodesOfInterest
//    }
//
//    x.forEach { println(it.key + ":" + it.value)}



    /**************************************************************************/
    /* I need to flag the first 'a' node in each item if it points to the same
       place as the item itself.  Actually it's now a moot point whether this
       information is useful or not. */

    doc.findNodesByName("item").forEach { item ->
      val firstA = item.getAllNodesBelow().find { it.nodeName == "a" }!!
      val aNodePointsTo = firstA["href"]!!.replace("?bref=", "")
      val itemPointsTo = item["name"]!!
      if (itemPointsTo == aNodePointsTo)
        firstA["X_FORCE_BOLDFACE"] = "y"
    }



    /**************************************************************************/
    /* At the end of this loop, the bulk of nodes will be unchanged.

       Those which lie at the start of an earlier summary section (summaries
       being recognisable because they cover ranges) will have had the summary
       information inserted at the start of their body details, and the summary
       item itself will no longer exist as an entity in its own right.
     */

    doc.findNodesByName("item").forEach {
      /************************************************************************/
      /* Are we dealing with an item which covers a range?  If so, reformat
         the content and save the revised details in pendingSummaryBody, along
         with details of the starting ref covered by the summary. */

      if ("-" in it["name"]!!)
      {
        pendingSummaryItemTag = it
        pendingRefAsString = ReferenceFormatter.restructureReference(it["name"]!!)
        pendingRefLowAsRefKey = ReferenceFormatter.toRefKey(pendingRefAsString.split("-")[0])

        val childNodes = Dom.getChildren(pendingSummaryItemTag!!)
        val refs = childNodes.first { child -> "refs" == Dom.getNodeName(child) }.textContent!!

        pendingSummary = childNodes.first { child -> "body" == Dom.getNodeName(child) }
        Dom.setNodeName(pendingSummary!!, "X_CONTAINER")
        val bold = Dom.createNode(doc, "<hi type='bold'/>")
        bold.appendChild(Dom.createTextNode(doc, "Summary for ${ReferenceFormatter.formatForHuman(refs)}: "))
        Dom.insertAsFirstChild(pendingSummary!!, bold)
        Dom.insertAsFirstChild(pendingSummary!!, CommonFormat.makeHeaderSeparator(doc))
        return@forEach
      }



      /************************************************************************/
      /* On individual refs, there's nothing to do unless we have a pending
         summary. */

      if (null == pendingSummary)
        return@forEach



      /************************************************************************/
      /* If we have a pending summary, we now have two possibilities.

         If the ref for the present item the same as the starting ref of the
         pending data, we need to add the new data into the content of the
         present item.

         Otherwise, we need to create a new item to cover the content, and
         insert this before present item. */

      val ref = ReferenceFormatter.restructureReference(it["name"]!!)
      if (pendingRefLowAsRefKey == ReferenceFormatter.toRefKey(ref)) // This item covers the same ground as the pending summary.
      {
        val body = Dom.getChildren(it).find { child -> "body" == Dom.getNodeName(child) }!!
        val bold = Dom.createNode(doc, "<hi type='bold'/>")
        bold.appendChild(Dom.createTextNode(doc, "Verse ${ReferenceFormatter.formatForHuman(ref)}:"))
        Dom.insertAsFirstChild(body, pendingSummary!!)
        Dom.deleteNode(pendingSummaryItemTag!!) // Drop the item tag itself -- we have extracted from it everything we need.
      }
      else
        pendingSummary!!["name"] = pendingRefAsString

      pendingSummaryItemTag = null // Any previous range item is no longer pending.
      pendingSummary = null // Any previous range item is no longer pending.
    }



    /**************************************************************************/
    doc.findNodesByName("refs").forEach(Dom::deleteNode)
    doc.findNodesByName("body").forEach { Dom.setNodeName(it, "X_CONTAINER") }
  }


  /****************************************************************************/
  /* Converts items to OSIS and prettifies them a bit. */

  private fun processItemsPart2 (doc: Document)
  {
    doc.findNodesByName("item").forEach { item ->
      val ref = ReferenceFormatter.restructureReference(item["name"]!!).split("-")[0]
      val sid = Dom.createNode(doc, "<div type='section' annotateType='commentary' annotateRef='$ref' sID='$ref'/>")
      val eid = Dom.createNode(doc, "<div type='section' annotateType='commentary' annotateRef='$ref' eID='$ref'/>")

      IntRange(0, 2).forEach {_ -> Dom.insertNodeBefore(item, Dom.createTextNode(doc, "\n")) } // Prettify the output.
      Dom.insertNodeBefore(item, Dom.createCommentNode(doc, "===================================================="))
      Dom.insertNodeBefore(item, Dom.createTextNode(doc, "\n"))

      Dom.insertNodeBefore(item, sid)

      val para = CommonFormat.makeParaBreak(doc, "99") // Bung children into a para.
      val itemChildren = Dom.getChildren(item) // Item's children.
      itemChildren.forEach(Dom::deleteNode)
      Dom.addChildren(para, itemChildren)
      Dom.insertNodeBefore(item, para) // Insert the para and then the eid before the item.
      Dom.insertNodeBefore(item, eid)
      Dom.deleteNode(item)             // The item is empty, so now we can delete it.
    }
  }


  /****************************************************************************/
  /* Converts the raw data to OSIS XML. */

  private fun convertToOsis (doc: Document)
  {
    makeOsisHeader(doc)
    doc.findNodesByAttributeValue("p", "class", "sn-text").forEach { Dom.promoteChildren(it); Dom.deleteNode(it) } // I don't think p:sn-text does anything useful.
    collapseWhitespace(doc) // Not particularly necessary, but makes things prettier.
    processItemsPart1(doc) // item tags need some heavy lifting.
    Dom.deleteNode(doc.findNodeByName("x2002")!!) // There seems to be one rogue tag which we don't want.
    addExternalInformation(doc, BookIntros::getSavedData)
    addExternalInformation(doc, BookIntroSummaries::getSavedData)
    CommonFormat.processSpans(doc) // They use span tags.  We need something else.
    CommonFormat.processParas(doc)
    processCrossReferences(doc)
    processVerticalWhitespaceBeforeHeaders(doc)
    doc.findNodesByName("body").forEach { Dom.promoteChildren(it); Dom.deleteNode(it) } // We don't need body tags, but we do want their content.
    processItemsPart2(doc)
    processTemporaryItems(doc)
    CommonFormat.processListItems(doc)
  }


  /****************************************************************************/
  private fun processTemporaryItems (doc: Document)
  {
    while (true)
    {
      val nodes = doc.findNodesByName("X_CONTAINER").filter { !Dom.hasAncestorNamed(it, "X_CONTAINER") }
      if (nodes.isEmpty())
        break
      else
        nodes.forEach {
          Dom.promoteChildren(it)
          Dom.deleteNode(it)
        }
    }
  }
}




/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
open class CommonDictionaryFormat (collectionType: String): CommonFormat()
{
  /****************************************************************************/
  override fun process (item: Node, firstRef: String, bookName: String, body: Node)
  {
    val key = item["name"]!!

    val containerNode = Dom.createNode(m_Doc, "<X_CONTAINER/>") // The outline is going in a temporary collector node.

    val title = body.findNodeByAttributeValue("p", "class", "$m_CollectionType-title")!!
    Dom.setNodeName(title, "hi")
    Dom.deleteAllAttributes(title)
    title["type"] = "bold"
    title.textContent = (if (m_CollectionType == "theme") "Thematic note: " else "Profile: ") + title.textContent
    Dom.deleteNode(title)

    containerNode.appendChild(makeParaBreak(m_Doc, "$m_CollectionType.1"))
    containerNode.appendChild(makeParaBreak(m_Doc, "$m_CollectionType.1"))
    containerNode.appendChild(title)
    containerNode.appendChild(makeParaBreak(m_Doc, "$m_CollectionType.2"))

    if (Dom.isWhitespace(body.firstChild))
      Dom.deleteNode(body.firstChild)

    val firstNode = body.firstChild // Sometimes the initial title is followed immediately by a subheader.
    if ("theme-h2" == firstNode["class"])
      firstNode["class"] = "theme-h2-noPrecedingWhitespace"

    containerNode.appendChild(body) // Now we want the actual textual content followed by another para break.

    m_Save[key] = SummaryThunk(bookName, firstRef, containerNode)
  }

  val m_CollectionType: String = collectionType
}

object Profiles: CommonDictionaryFormat("profile")
object ThemeNotes: CommonDictionaryFormat("theme")





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
object ExternalInterface
{
  fun createModuleZip(fileLocationsGroup: FileLocations.FileLocationsGroup)
  {
    val zipPath: String = fileLocationsGroup.swordZipFilePath()
    val inputs = mutableListOf(fileLocationsGroup.swordConfigFolderPath(), fileLocationsGroup.swordTextFolderPath())
    Zip.createZipFile(zipPath, 9, fileLocationsGroup.swordRootFolderPath(), inputs)
  }


  /****************************************************************************/
  fun handleOsis2modCall (fileLocations: FileLocations.FileLocationsGroup)
  {
    val programPath = File(FileLocations.osis2ModFilePath).toString()
    val swordExternalConversionCommand: MutableList<String> = ArrayList()
    swordExternalConversionCommand.add(programPath) // Don't enclose the path in quotes -- see note above.
    swordExternalConversionCommand.add(fileLocations.swordTextFolderPath())
    swordExternalConversionCommand.add(fileLocations.osisFilePath())
    swordExternalConversionCommand.add("-v")
    swordExternalConversionCommand.add("KJV")
    swordExternalConversionCommand.add("-z")
    runCommand("Running external command to generate Sword data: ", swordExternalConversionCommand, fileLocations.errorFilePath())
  }


  /****************************************************************************/
  private fun quotify (s: String, quote: String = "\"") = quote + s + quote
  private fun quotifyIfContainsSpaces (s: String, quote: String = "\"") = if (" " in s) quotify(s, quote) else s


  /****************************************************************************/
  private fun runCommand (prompt: String?, command: List<String>, errorFilePath: String? = null, workingDirectory: String? = null): Int
  {
    println(prompt + command.joinToString(" "){ quotifyIfContainsSpaces(it) })
    val pb = ProcessBuilder(command)

    if (null != errorFilePath)
    {
      pb.redirectOutput(File(errorFilePath))
      pb.redirectError(File(errorFilePath))
    }

    if (null != workingDirectory) pb.directory(File(workingDirectory))
    val res = pb.start().waitFor()
    return res
  }
}



/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
object ReferenceFormatter
{
  /****************************************************************************/
  private data class Ref (var book: String?, var chapter: String?, var verse: String)


  /****************************************************************************/
  fun formatForHuman (ref: String): String
  {
    /**************************************************************************/
    val bits = ref.split("-")
    val books: MutableList<String?> = mutableListOf()
    val chapters: MutableList<String?> = mutableListOf()
    val verses: MutableList<String?> = mutableListOf()



    /**************************************************************************/
    for (i in bits.indices)
    {
      val subRef = parseRef(bits[i])
      books.add(subRef.book)
      chapters.add(subRef.chapter)
      verses.add(subRef.verse)
    }



    /**************************************************************************/
    fun makeRef (ix: Int): String
    {
      var bcSep = ""; var cvSep = ""

      if (null != books[ix] && null != chapters[ix])
        bcSep = " "

      if (null != chapters[ix] && null != verses[ix])
        cvSep = ":"

      return (books[ix] ?: "") + bcSep + (chapters[ix] ?: "") + cvSep + (verses[ix] ?: "")
    }



    /**************************************************************************/
    if (1 == bits.size)
      return makeRef(0)
    else
    {
      val part1 = makeRef(0)

      if (books[0] == books[1])
        books[1] = null

      if (chapters[0] == chapters[1])
      {
        chapters[1] = null

        if (verses[0] == verses[1])
          verses[1] = null
      }

      var part2 = makeRef(1)
      part2 = if (part1 == part2) "" else "-$part2"
      return "$part1$part2"
    }
  }


  /****************************************************************************/
  private fun parseRef (ref: String): Ref
  {
    val bits = ref.split(".")
    val book = if (bits[0].matches("(?i).*[a-z]{2}".toRegex())) bits[0] else null
    return if (null == book)
    {
      if (1 == bits.size)
        Ref(null, null, bits[0])
      else
        Ref(null, bits[0], bits[1])
    }
    else
      Ref(book, bits[1], if (3 == bits.size) bits[2] else "0")
  }


  /****************************************************************************/
  fun restructureReference (ref: String): String
  {
    if ('-' in ref)
    {
      val lowRef = parseRef(ref.split("-")[0])
      val highRef = parseRef(ref.split("-")[1])
      if (null == highRef.book) highRef.book = lowRef.book
      if (null == highRef.chapter) highRef.chapter = lowRef.chapter
      return lowRef.book + "." + lowRef.chapter + "." + lowRef.verse + "-" + highRef.book + "." + highRef.chapter + "." + highRef.verse
    }
    else
      return ref
  }


  /****************************************************************************/
  fun toRefKey (ref: String): Int
  {
    val parsed = parseRef(ref)
    val verseNo = if ("title" == parsed.verse) 0 else parsed.verse.replace("[a-z]+".toRegex(), "").toInt()
    return 1000 * parsed.chapter!!.toInt() + verseNo
  }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
object FileLocations
{
  const val osis2ModFilePath = "C:/Program Files/Jamie/STEP/SamiOsis2mod/samisOsis2Mod.exe"
  const val rootFolderPath = "C:/Users/Jamie/RemotelyBackedUp/Git/StepTextConversion/Texts/CommentariesEtc/Text_eng_TNotes"
  var moduleName = ""


  interface FileLocationsGroup
  {
    fun inputFilePath (): String
    fun osisFilePath (): String
    fun rootFolderPath (): String

    fun errorFilePath () = Paths.get(outputFolderPath(), "converterLog.txt").toString()
    fun outputFolderPath () = Paths.get(rootFolderPath(), "_OutputPublic").toString()
    fun swordRootFolderPath () = Paths.get(outputFolderPath(), "Sword").toString()
    fun swordConfigFolderPath (): String = Paths.get(swordRootFolderPath(), "mods.d").toString()
    fun swordTextFolderPath () = Paths.get(outputFolderPath(), "Sword", "modules", "texts", "ztext", moduleName).toString()
    fun swordZipFilePath () = Paths.get(outputFolderPath(), "$moduleName.zip").toString()
  }

  object BookIntros: FileLocationsGroup
  {
    override fun rootFolderPath () = rootFolderPath
    override fun inputFilePath () = Paths.get(rootFolderPath(), "InputProprietary", "BookIntros.xml").toString()
    override fun osisFilePath () = TODO()
  }

  object BookIntroSummaries: FileLocationsGroup
  {
    override fun rootFolderPath () = rootFolderPath
    override fun inputFilePath () = Paths.get(rootFolderPath(), "InputProprietary", "BookIntroSummaries.xml").toString()
    override fun osisFilePath () = TODO()
  }

  object Profiles: FileLocationsGroup
  {
    override fun rootFolderPath () = rootFolderPath
    override fun inputFilePath () = Paths.get(rootFolderPath(), "InputProprietary", "Profiles.xml").toString()
    override fun osisFilePath () = TODO()
  }

  object StudyNotes: FileLocationsGroup
  {
    override fun rootFolderPath () = rootFolderPath
    override fun inputFilePath () = Paths.get(rootFolderPath(), "InputProprietary", "StudyNotes.xml").toString()
    override fun osisFilePath () = Paths.get(rootFolderPath(), "InputOsis", "studyNotesOsis.xml").toString()
  }

  object Themes: FileLocationsGroup
  {
    override fun rootFolderPath () = rootFolderPath
    override fun inputFilePath () = Paths.get(rootFolderPath(), "InputProprietary", "ThemeNotes.xml").toString()
    override fun osisFilePath () = TODO()
  }
}

