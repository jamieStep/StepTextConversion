package org.stepbible.tipnrchecker

/******************************************************************************/
/**
 * Miscellaneous functionality to check and suggest changes to TIPNR data.
 *
 */

import net.sf.saxon.lib.NamespaceConstant
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.TreeMap
import java.util.function.Consumer
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.text.contains



/******************************************************************************/
fun main ()
{
  SimpleNameValidatorAndCorrector.process()
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/**
 * Deals with things like names which include vertical bars and parens.  In the
 * original TIPNR data, these were a kind of shorthand from which you were
 * required to deduce the actual name string, but there were many cases where
 * that was impossible. */

object SimpleNameValidatorAndCorrector
{
  /****************************************************************************/
  private const val C_FolderPath = "C:/Users/Jamie/Desktop/Tipnr/"
  private lateinit var m_Esv: BibleText
  private lateinit var m_Kjv: BibleText
  private lateinit var m_Niv: BibleText
  private lateinit var m_StuffToBeChecked: List<MutableList<String>>

  private const val C_Ix_TipnrName                      = 0
  private const val C_Ix_TipnrNameWithoutPunctuation    = 1
  private const val C_Ix_Texts                          = 2
  private const val C_Ix_Refs                           = 3
  private const val C_Ix_Checks                         = 4 // Leave a column for use by the spreadsheet.
  private const val C_Ix_NameToBeUsed                   = 5
  private const val C_Ix_Verses                         = 6


  /****************************************************************************/
  fun process ()
  {
    m_Esv = BibleText(C_FolderPath + "ESV2025_OSIS+Strongs+morph.xml", "ESV")
    m_Kjv = BibleText(C_FolderPath + "KJVA.xml", "KJV")
    m_Niv = BibleText(C_FolderPath + "en_NIV_osis.xml", "NIV")

    processParensEtc(m_Esv)
    processParensEtc(m_Kjv)
    processParensEtc(m_Niv)
  }


  /****************************************************************************/
  private fun processParensEtc (bibleText: BibleText)
  {
    m_StuffToBeChecked = readStuffToBeChecked(C_FolderPath + "stuffToBeChecked.tsv")

    for (ix in 1 ..< m_StuffToBeChecked.size)
      processParensEtc(ix, bibleText)

    File(C_FolderPath + "stuffToBeChecked_${bibleText.m_Abbrev}.tsv").writeText(m_StuffToBeChecked.joinToString("\n") { subList -> subList.joinToString("\t") })
  }


  /****************************************************************************/
  private fun processParensEtc (rowIx: Int, bibleText: BibleText)
  {
    /**************************************************************************/
    val row = m_StuffToBeChecked[rowIx]
    if (row.size < 2)
      return



    /**************************************************************************/
    val tipnrName = row[C_Ix_TipnrName]
    val texts = row[C_Ix_Texts]
    val refs = row[C_Ix_Refs]

    if (bibleText.m_Abbrev !in texts)
    {
      row[C_Ix_NameToBeUsed] = "THIS_ROW_NOT_FOR_${bibleText.m_Abbrev}"
      return
    }

    if ("LXX" in refs)
    {
      row[C_Ix_NameToBeUsed] = "LXX_HANDLE_MANUALLY"
      return
    }

    var bareName = row[C_Ix_NameToBeUsed]
    if ("?" == bareName)
      bareName = tipnrName.replace("(", "")
        .replace(")", "")
        .replace("|", " ")
        .replace("_", " ")
        .trim()
        .replace("\\s+".toRegex(), " ")

    val verses = refs.split(";").map { usxRef ->
      val b = ReferenceConverter.usxToOsis(usxRef.substring(0, 3))
      val cv = usxRef.substring(3).trimEnd { it.isLowerCase() }
      bibleText.getTextContent(b + cv)
    }

    row[C_Ix_TipnrNameWithoutPunctuation] = bareName

    var firstVerse = true
    verses.forEach {
      if (firstVerse)
        row[C_Ix_Verses] = it
      else
        row.add(it)
      firstVerse = false
    }

    val prefix = bibleText.m_Abbrev + ":"
    if (verses.all { bareName in it })
      m_StuffToBeChecked[rowIx][C_Ix_NameToBeUsed] = bareName
    else if (verses.any { bareName in it})
      m_StuffToBeChecked[rowIx][C_Ix_NameToBeUsed] = "??? $bareName"
    else
      m_StuffToBeChecked[rowIx][C_Ix_NameToBeUsed] = "NOT_FOUND"
  }


  /****************************************************************************/
  private fun readStuffToBeChecked (filePath: String): List<MutableList<String>> {
    val text = File(filePath).readText()
    val res = text.split("\n").map { it.split("\t").map { it.trim() }.toMutableList() }
    res.subList(0, res.size - 1).forEach { subList -> subList.subList(C_Ix_Verses + 1, subList.size).clear() }
    return res
  }


  /****************************************************************************/
  private val C_StuffToBeChecked =
"""

""".trimIndent()
} // SimpleNameValidatorAndCorrector





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
class BibleText (val fileName: String, abbrev: String)
{
  /****************************************************************************/
  val bibleAbbreviatedName = abbrev


  /****************************************************************************/
  fun getTextContent (ref: String): String
  {
    /**************************************************************************/
    fun ofInterest (node: Node): Boolean
    {
      if (!Dom.isTextNode(node)) return false
      if (Dom.hasAncestorNamed(node, "note"))  return false
      if (Dom.hasAncestorNamed(node, "title")) return false
      return true
    }



    /**************************************************************************/
    val boundaries = m_VerseBoundaries[ref]!!
    var res = StringBuilder()
    for (ix in boundaries.start + 1 ..< boundaries.end)
    {
      val node = m_AllNodes[ix]
      if (ofInterest(node) && node.textContent.isNotEmpty())
      {
        res.append(" ")
        res.append(node.textContent)
      }
    }



    /**************************************************************************/
    return res.trim().replace("\\s+".toRegex(), " ")
  }


  /****************************************************************************/
  data class VerseBoundaries (var start: Int = 0, var end: Int = 0)

  /****************************************************************************/
  val m_Abbrev = abbrev
  var m_AllNodes: List<Node>
  val m_VerseBoundaries: MutableMap<String, VerseBoundaries> = mutableMapOf()
  init
  {
    /**************************************************************************/
    val dom = Dom.getDocument(fileName)
    m_AllNodes = Dom.getAllNodesBelow(dom)
    var b = VerseBoundaries(0, 0)



    /**************************************************************************/
    /* Most texts use milestone verse tags, but the NIV I have been given
       uses enclosing nodes.  It's convenient to convert this to milestones. */

    var modified = false

    m_AllNodes.forEach { node ->
      if ("verse" != Dom.getNodeName(node) || !node.hasChildNodes()) return@forEach
      val ref = node["osisID"]
      val sid = Dom.createNode(node.ownerDocument, "<verse osisID='$ref' sID='$ref'/>")
      val eid = Dom.createNode(node.ownerDocument, "<verse osisID='$ref' eID='$ref'/>")
      Dom.insertNodeBefore(node, sid)
      node.appendChild(eid)
      Dom.promoteChildren(node)
      Dom.deleteNode(node)
      modified = true
    }

    if (modified)
      m_AllNodes = Dom.getAllNodesBelow(dom)



    /**************************************************************************/
    /* Build a map relating refs to start and end indices. */

    m_AllNodes.indices.forEach { ix ->
      val node = m_AllNodes[ix]
      val nodeName = Dom.getNodeName(node)

      if ("verse" == nodeName)
      {
        if ("sID" in node)
        {
          b = VerseBoundaries(ix, 0)
          m_VerseBoundaries[node["osisID"]!!] = b
        }
        else
          b.end = ix
      }
    }
  }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
object ReferenceConverter
{
  /****************************************************************************/
  fun usxToOsis (usx: String): String = m_UsxToOsis[usx]!!


  /****************************************************************************/
  val m_UsxToOsis = mapOf(
    "Gen" to "Gen",
    "Exo" to "Exod",
    "Lev" to "Lev",
    "Num" to "Num",
    "Deu" to "Deut",
    "Jos" to "Josh",
    "Jdg" to "Judg",
    "Rut" to "Ruth",
    "1Sa" to "1Sam",
    "2Sa" to "2Sam",
    "1Ki" to "1Kgs",
    "2Ki" to "2Kgs",
    "1Ch" to "1Chr",
    "2Ch" to "2Chr",
    "Ezr" to "Ezra",
    "Neh" to "Neh",
    "Est" to "Esth",
    "Job" to "Job",
    "Psa" to "Ps",
    "Pro" to "Prov",
    "Ecc" to "Eccl",
    "Sng" to "Song",
    "Isa" to "Isa",
    "Jer" to "Jer",
    "Lam" to "Lam",
    "Ezk" to "Ezek",
    "Dan" to "Dan",
    "Hos" to "Hos",
    "Jol" to "Joel",
    "Amo" to "Amos",
    "Oba" to "Obad",
    "Jon" to "Jonah",
    "Mic" to "Mic",
    "Nam" to "Nah",
    "Hab" to "Hab",
    "Zep" to "Zeph",
    "Hag" to "Hag",
    "Zec" to "Zech",
    "Mal" to "Mal",
    "Mat" to "Matt",
    "Mrk" to "Mark",
    "Luk" to "Luke",
    "Jhn" to "John",
    "Act" to "Acts",
    "Rom" to "Rom",
    "1Co" to "1Cor",
    "2Co" to "2Cor",
    "Gal" to "Gal",
    "Eph" to "Eph",
    "Php" to "Phil",
    "Col" to "Col",
    "1Th" to "1Thess",
    "2Th" to "2Thess",
    "1Ti" to "1Tim",
    "2Ti" to "2Tim",
    "Tit" to "Titus",
    "Phm" to "Phlm",
    "Heb" to "Heb",
    "Jas" to "Jas",
    "1Pe" to "1Pet",
    "2Pe" to "2Pet",
    "1Jn" to "1John",
    "2Jn" to "2John",
    "3Jn" to "3John",
    "Jud" to "Jude",
    "Rev" to "Rev",
    "Tob" to "Tob",
    "Jdt" to "Jdt",
    "Esg" to "EsthGr",
    "Wis" to "Wis",
    "Sir" to "Sir",
    "Bar" to "Bar",
    "Lje" to "EpJer",
    "S3y" to "PrAzar",
    "Sus" to "Sus",
    "Bel" to "Bel",
    "1Ma" to "1Macc",
    "2Ma" to "2Macc",
    "3Ma" to "3Macc",
    "4Ma" to "4Macc",
    "1Es" to "1Esd",
    "2Es" to "2Esd",
    "Man" to "PrMan",
    "Ps2" to "AddPs",
    "Oda" to "Odes",
    "Pss" to "PssSol",
    "Jsa" to "JoshA",
    "Jdb" to "JudgB",
    "Tbs" to "TobS",
    "Sst" to "SusTh",
    "Dnt" to "DanTh",
    "Blt" to "BelTh",
    "Lao" to "EpLao",
    "Eza" to "4Ezra",
    "5Ez" to "5Ezr2",
    "6Ez" to "6Ezra"
  )
}


/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
operator fun Node.get (attributeName: String): String? { return Dom.getAttribute(this, attributeName) }           // val x = node[attrName"]
operator fun Node.set (attributeName: String, value: String) { Dom.setAttribute(this, attributeName, value) }     // node["attrName"] = value
operator fun Node.contains (attributeName: String): Boolean { return Dom.hasAttribute(this, attributeName) }      // "attrName" in node (or !in)
fun Node.hasAncestorNamed (name: String) = Dom.hasAncestorNamed(this, name)
fun Node.stringify () = Dom.toString(this)

private object Dom
{
  /****************************************************************************/
  /**
   * Adds a list of children to the existing children of a given node.  Note
   * that there is no need for the children and the new parent to be in the same
   * document.
   *
   * @param parent Parent node.
   * @param newChildren Children to be added (null or empty are ok).
   */

  fun addChildren (parent: Node, newChildren: List<Node>?)
  {
    if (newChildren.isNullOrEmpty()) return

    val parentOwnerDocument = parent.ownerDocument
    if (newChildren[0].ownerDocument === parentOwnerDocument)
      newChildren.forEach { n: Node? -> parent.appendChild(n) }
    else
      newChildren.forEach { n: Node -> parent.appendChild(createNode(parentOwnerDocument, n, true)) }
  }


  /****************************************************************************/
  /**
   * Turns an XML string into a node.
   *
   * @param doc Owning document.
   * @param xml XML.
   * @return Node.
   */

  fun createNode (doc: Document, xml: String): Node
  {
    val n: Node = DocumentBuilderFactory
      .newInstance()
      .newDocumentBuilder()
      .parse(ByteArrayInputStream(xml.toByteArray()))
      .documentElement
    return doc.importNode(n, true)
  }


  /****************************************************************************/
  /**
   * Creates a new node by cloning an existing one.
   *
   * @param doc Document within which node is created.
   * @param node Node to be copied.
   * @param andSubstructure If true, all descendants are also copied.
   * @return New node.
   */

  fun createNode (doc: Document, node: Node, andSubstructure: Boolean): Node
  {
    val tagName = getNodeName(node)
    if ("#text" == tagName) return doc.createTextNode(node.textContent)
    if ("#comment" == tagName) return doc.createComment(node.textContent)
    val res = createNode(doc, tagName, getAttributes(node))
    if (!andSubstructure) return res
    val nl = node.childNodes
    for (i in 0..< nl.length) res.appendChild(createNode(doc, nl.item(i), true))
    return res
  }


  /****************************************************************************/
  /**
   * Creates a new node (*except* a comment node -- the routine doesn't
   * handle them).
   *
   * @param doc Document within which node is created.
   * @param nodeName Name to be given to node.
   * @param attributes Attributes (supply null if no attributes).
   * @return New node.
   */

  fun createNode (doc: Document, nodeName: String, attributes: Map<String, String>?): Node
  {
    val e = doc.createElement(nodeName)
    val n = e as Node
    attributes?.keys?.forEach { setAttribute(n, it, attributes[it]!!) }
    return n
  }


  /****************************************************************************/
  /**
   * Deletes an attribute.  Does not worry if the attribute does not exist.
   *
   * @param node Node.
   * @param attributeName Attribute name.
   */

  fun deleteAttribute(node: Node, attributeName: String) {
    try
    {
      (node as Element).removeAttribute(attributeName)
    }
    catch (_: Exception)
    {
    }
  }


  /****************************************************************************/
  /**
   * Deletes a given node, taking its children with it.
   *
   * @param node Node to be deleted.
   * @return The node which has been deleted.
   */

  fun deleteNode (node: Node): Node
  {
    getParent(node)?.removeChild(node)
    return node
  }


  /****************************************************************************/
  /**
   * Deletes a collection of nodes recursively.
   *
   * @param nodes List of nodes to be cloned.
   */

  fun deleteNodes (nodes: List<Node>)
  {
    val safeDelete = Consumer { node: Node ->
      try
      {
        getParent(node)?.removeChild(node)
      }
      catch (_: Exception)
      {
      }
    }

    nodes.forEach { node: Node -> safeDelete.accept(node) }
  }


  /****************************************************************************/
  /**
   * Finds all the text nodes under a given node.  You don't seem to be able
   * to use, for this purpose, the other methods I have set up, because they
   * generally rely upon checking the names of nodes, and text nodes apparently
   * don't really have a name.
   *
   * @param n Node under which we look for text nodes.  This node is excluded
   *          from the output even if it is itself a text node.
   *
   * @return List of text nodes (possibly empty).
   */

  fun findAllTextNodes (n: Node): List<Node>
  {
    val res: MutableList<Node> = ArrayList()
    val children = n.childNodes
    for (i in 0..< children.length)
    {
      val child = children.item(i)
      if (child.nodeType == Node.TEXT_NODE) res.add(child) else res.addAll(findAllTextNodes(child))
    }

    return res
  }


  /****************************************************************************/
  /**
   * Returns the closest ancestor of a given node having a given name, or null.
   *
   * @param descendant Node from which we work.
   * @param nodeName Node name of ancestor.
   * @return Ancestor.
   */

  fun findAncestorByNodeName (descendant: Node, nodeName: String): Node?
  {
    var n: Node? = descendant
    while (true)
    {
      n = getParent(n!!)
      if (null == n) break
      if (nodeName == getNodeName(n)) return n
    }

    return null
  }


  /****************************************************************************/
  /**
   * Finds the first node of a given type having a given attribute with a given
   * value.  Matching is case-insensitive.
   *
   * @param doc Owning document.
   * @param nodeName Node type.
   * @param attributeName Name of attribute.
   * @param value Exact value of attribute (except case-insensitive).
   * @return Node or null.
   */

  fun findNodeByAttributeValue (doc: Document, nodeName: String, attributeName: String, value: String): Node?
  {
    return findNodeByAttributeValue(doc.documentElement, nodeName, attributeName, value)
  }


  /****************************************************************************/
  /**
   * Finds the first node of a given type having a given attribute with a given
   * value.  Matching is case-insensitive.
   *
   * @param parent Node below which other nodes are located.
   * @param nodeName Node type.
   * @param attributeName Name of attribute.
   * @param value Exact value of attribute (except case-insensitive).
   * @return Node or null.
   */

  fun findNodeByAttributeValue (parent: Node, nodeName: String, attributeName: String, value: String): Node?
  {
    return findNodeByAttributeValueInternal(parent, nodeName, attributeName, value)
  }


  /****************************************************************************/
  /**
   * Returns the first node with a given name under a given node, or null if not
   * found.
   *
   * IMPORTANT: You can't use this method to find text nodes.
   *
   * @param node Owning node.
   * @param nodeName Node name.
   * @param includeRoot True if root note is to be included in search.
   * @return The selected node.
   */

  fun findNodeByName (node: Node, nodeName: String, includeRoot: Boolean): Node?
  {
    val res: Node?
    if (includeRoot && nodeName == getNodeName(node))
      res = node
    else
    {
      val xPathFactory = XPathFactory.newInstance()
      val xPath = xPathFactory.newXPath()
      val expr = xPath.compile(".//*[local-name()='$nodeName'][1]")
      res = expr.evaluate(node, XPathConstants.NODE) as Node?
    }

    return res
  }


  /****************************************************************************/
  /**
   * Returns a list of nodes of a given type having an attribute with a given
   * name.
   *
   * @param doc Owning document.
   * @param nodeName Node type.
   * @param attributeName Name of attribute.
   * @return Node list or null.
   */

  fun findNodesByAttributeName (doc: Document, nodeName: String, attributeName: String): List<Node>
  {
    return findNodesByAttributeName(doc.documentElement, nodeName, attributeName)
  }


  /****************************************************************************/
  /**
   * Returns a list of nodes of a given type having an attribute with a given
   * name.
   *
   * @param n Owning node.
   * @param nodeName Node type.
   * @param attributeName Name of attribute.
   * @return Node list or null.
   */

  fun findNodesByAttributeName (n: Node?, nodeName: String, attributeName: String, xPathConstant: javax.xml.namespace.QName = XPathConstants.NODESET): List<Node>
  {
    val xPathFactory = XPathFactory.newInstance()
    val xPath = xPathFactory.newXPath()
    val expr = xPath.compile(".//*[local-name()='$nodeName' and @$attributeName]")
    val res = expr.evaluate(n, xPathConstant) as NodeList
    return toListOfNodes(res)
  }


  /****************************************************************************/
  /**
   * Finds the first node of a given type having a given attribute with a given
   * value.  Matching is case-insensitive.
   *
   * @param doc Owning document.
   * @param nodeName Node type.
   * @param attributeName Name of attribute.
   * @param value Exact value of attribute (except case-insensitive).
   * @return Node or null.
   */

  fun findNodesByAttributeValue (doc: Document, nodeName: String, attributeName: String, value: String): List<Node>
  {
    return findNodesByAttributeValueInternal(doc.documentElement, nodeName, attributeName, value)
  }


  /****************************************************************************/
  /**
   * Finds the first node of a given type having a given attribute with a given
   * value.  Matching is case-insensitive.
   *
   * @param parent Node below which other nodes are located.
   * @param nodeName Node type.
   * @param attributeName Name of attribute.
   * @param value Exact value of attribute (except case-insensitive).
   * @return Node or null.
   */

  fun findNodesByAttributeValue (parent: Node, nodeName: String, attributeName: String, value: String): List<Node>
  {
    return findNodesByAttributeValueInternal(parent, nodeName, attributeName, value)
  }


  /****************************************************************************/
  /**
   * Returns a list containing all nodes with a given name, or null if not
   * found.
   *
   *
   * IMPORTANT: You can't use this method to find text nodes.
   *
   * @param doc Document.
   * @param nodeName Node name.
   * @return List of nodes.
   */

  fun findNodesByName (doc: Document, nodeName: String?): List<Node>
  {
    return toListOfNodes(doc.getElementsByTagName(nodeName))
  }


  /****************************************************************************/
  /**
   * Returns a list containing all nodes with a given name, or null if not
   * found.
   *
   *
   * IMPORTANT: You can't use this method to find text nodes, although if a
   * text node is passed as the first argument, it will at least cope.
   *
   * @param node Parent node.
   * @param nodeName Node name.
   * @param includeParent If true, the parent node is included in the results.
   * @return List of nodes.
   */

  fun findNodesByName (node: Node, nodeName: String, includeParent: Boolean): List<Node>
  {
    if ("#text" == getNodeName(node)) return ArrayList()
    val res = toListOfNodes((node as Element).getElementsByTagName(nodeName)).toMutableList()
    if (includeParent && nodeName == getNodeName(node)) res.add(0, node)
    return res
  }


  /****************************************************************************/
  /**
   * Generates a collection of all the nodes below a given node, depth first.
   *
   * @param startNode Starting node.
   * @return List of nodes.
   */

  fun getAllNodesBelow (startNode: Node): List<Node>
  {
    val res: MutableList<Node> = ArrayList()
    getAllNodesBelow(res, startNode)
    return res
  }


  /****************************************************************************/
  /**
   * If a node has an ancestor with a given name, returns that ancestor.
   * Otherwise returns null.
   *
   * @param n Node.
   * @param ancestorName Required name.
   * @return Nearest ancestor of the given name, or null.
   */

  fun getAncestorNamed (n: Node, ancestorName: String): Node?
  {
    var parent: Node? = n
    while (true)
    {
      parent = getParent(parent!!)
      if (null == parent || parent is Document) break
      if (getNodeName(parent) == ancestorName) return parent
    }

    return null
  }


  /****************************************************************************/
  /**
   * Returns a given attribute value, or null if there is no such attribute.
   *
   * @param node Node.
   * @param attributeName  Name of attribute.
   * @return Attribute value, or null
   */

  fun getAttribute (node: Node, attributeName: String): String?
  {
    if (!node.hasAttributes()) return null
    val n = node.attributes.getNamedItem(attributeName) ?: return null
    return n.nodeValue
  }


  /****************************************************************************/
  /**
   * Returns the attributes of the given node.  (The map may be empty if there
   * are no attributes.)
   *
   * @param node Node.
   * @return  Attributes.
   */

  fun getAttributes (node: Node): Map<String, String>
  {
    val res: MutableMap<String, String> = TreeMap<String, String>(java.lang.String.CASE_INSENSITIVE_ORDER)
    if (!node.hasAttributes()) return res
    val attributeMap: NamedNodeMap = node.attributes
    for (i in 0..< attributeMap.length)
    {
      val n: Node = attributeMap.item(i)
      res[n.nodeName] = n.nodeValue
    }

    return res
  }


  /****************************************************************************/
  /**
   * Gets the children of a given node as a list of nodes.
   *
   * @param node Node whose children are required.
   * @return List of child nodes.
   */

  fun getChildren (node: Node): List<Node>
  {
    val children = node.childNodes
    val res: MutableList<Node> = ArrayList(children.length)
    for (i in 0..< children.length) res.add(children.item(i))
    return res
  }


  /****************************************************************************/
  /**
   * Returns the DOM document corresponding to a given input file.
   *
   * @param inputFilePath Input file path.
   * @param retainComments By default, comments are removed.  This retains them.
   * @return DOM document.
   * @throws Exception Any exception.
   */

  fun getDocument (inputFilePath: String, retainComments: Boolean = false): Document = getDocumentFromText(File(inputFilePath).readText(), retainComments)



  /****************************************************************************/
  /**
   * Returns the DOM document corresponding to a given input file.
   *
   * @param text XML text.
   * @param retainComments By default, comments are removed.  This retains them.
   * @return DOM document.
   * @throws Exception Any exception.
   */

  fun getDocumentFromText (text: String, retainComments: Boolean = false): Document
  {
    /************************************************************************/
    /* There is a slightly awkward decision as to whether the factory should
       be made namespace aware below.  Parsing USX is fine because it has no
       namespace setting.  But parsing OSIS (which recent requirement changes
       have made necessary) is a problem, because according to the
       documentation you do need namespace details there.

       If you don't use namespace awareness, you get a warning message when
       processing.  If you _do_ use it, you have to be careful within the
       implementation of the various find* methods here to use local-name()
       to pick up the node names.

       (For some reason, findNodesByName doesn't seem to be affected by this
       issue.) */

    System.setProperty("javax.xml.xpath.XPathFactory:" + NamespaceConstant.OBJECT_MODEL_SAXON, "net.sf.saxon.xpath.XPathFactoryImpl")
    val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = false // See comments above.
    factory.isIgnoringComments = !retainComments
    factory.isIgnoringElementContentWhitespace = true
    factory.isCoalescing = true
    val builder: DocumentBuilder = factory.newDocumentBuilder()
    val inputData = ByteArrayInputStream(text.replace("\u00a0", "&#160;").toByteArray()) // Unicode non-breaking space.
    val doc =  builder.parse(inputData)
    doc.normalizeDocument()
    return doc
  }


  /****************************************************************************/
  /**
   * Returns the next sibling of a given node, or null if there are no more.
   *
   * @param node Node whose sibling is required.
   * @return Sibling, or null.
   */

  fun getNextSibling (node: Node): Node?
  {
    return node.nextSibling
  }


  /****************************************************************************/
  /**
   * Returns the name of the node.
   *
   * @param node Node of interest.
   * @return Node name.
   */

  fun getNodeName (node: Node): String
  {
    return node.nodeName
  }


  /****************************************************************************/
  /**
   * Returns the parent of a given node.
   *
   * @param child Child.
   * @return Parent.
   */

  fun getParent(child: Node): Node?
  {
    return child.parentNode
  }


  /****************************************************************************/
  /**
   * Returns the previous sibling of a given node, or null if there is none.
   *
   * @param node Node whose sibling is required.
   * @return Sibling, or null.
   */

  fun getPreviousSibling (node: Node): Node?
  {
    return node.previousSibling
  }


  /****************************************************************************/
  /**
   * Checks if a given node has a given attribute.
   *
   * @param node Node.
   * @param attributeName  Name of attribute.
   * @return True if node has the given attribute.
   */

  fun hasAttribute (node: Node, attributeName: String): Boolean
  {
    return null != getAttribute(node, attributeName)
  }


  /****************************************************************************/
  /**
   * Checks if a given node has an ancestor with a given name.
   *
   * @param n Node.
   * @param ancestorName Required name.
   * @return True if the node has an ancestor of the given name.
   */

  fun hasAncestorNamed (n: Node, ancestorName: String): Boolean
  {
    val ancestor = getAncestorNamed(n, ancestorName)
    return null != ancestor
  }


  /****************************************************************************/
  /**
   * Inserts a node following another node and as a sibling of it.
   *
   * @param target Node before which new node is to be inserted.
   * @param newNode New node.
   */

  fun insertNodeAfter (target: Node, newNode: Node)
  {
    val nextSibling = getNextSibling(target)
    val parent = getParent(target)!!
    if (null == nextSibling) parent.appendChild(newNode) else insertNodeBefore(nextSibling, newNode)
  }


  /****************************************************************************/
  /**
   * Inserts a node prior to another node and as a sibling of it.
   *
   * @param target Node before which new node is to be inserted.
   * @param newNode New node.
   */

  fun insertNodeBefore (target: Node, newNode: Node)
  {
    getParent(target)!!.insertBefore(newNode, target)
  }


  /****************************************************************************/
  /**
   * Checks if a given node is a text node.
   *
   * @param node Node to be tested.
   * @return True if text node.
   */

  fun isTextNode (node: Node) = "#text" == getNodeName(node)


  /****************************************************************************/
  /**
   * Inserts a list of nodes prior to another node and as siblings of it.
   *
   * @param target Node before which new nodes are to be inserted.
   * @param newNodes List of new nodes.
   */

  fun insertNodesBefore(target: Node, newNodes: List<Node>)
  {
    for (i in newNodes.indices) insertNodeBefore(target, newNodes[i])
  }


  /****************************************************************************/
  /** Converts a DOM to a string representation.
   *
   * @param doc
   * @return String representation.
   */

  fun outputDomAsString (doc: Document): String
  {
    val transformerFactory = TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()

    // Optional: Set output properties
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

    val writer = StringWriter()
    val result = StreamResult(writer)
    val source = DOMSource(doc)

    transformer.transform(source, result)

    return writer.toString()
  }


  /****************************************************************************/
  /**
   * Outputs the DOM as XML, to a file or to System.out.
   *
   * @param doc Document.
   * @param filePath File or null if you want output to System.out.
   * @param theComment Any comment which you wish to have at the top of the file,
   *   or null.
   * @param textPatchUpFn Makes any changes to the text.  For example, I've
   *   found it necessary to replace &lt; and &gt; to ^lt; and ^gt; in some
   *   places because I've been processing USX which contains these entities,
   *   and left as such in the input, they mess things up.  But having done that,
   *   I need to change them back again here.
   */

  fun outputDomAsXml (doc: Document, filePath: String?, theComment: String?, textPatchUpFn: ((String) -> String)? = null)
  {
    doc.normalize()
    val transformer: Transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "no")
    val result = StreamResult(StringWriter())
    val source = DOMSource(doc)
    transformer.transform(source, result)
    var xmlOutput = result.writer.toString().replace("\r", "")
    if (null != textPatchUpFn) xmlOutput = textPatchUpFn(xmlOutput)
    val ix = xmlOutput.indexOf(">") + 1
    val firstBit = xmlOutput.substring(0, ix)
    val comment = if (null == theComment) "" else "<!-- $theComment -->\n"
    val secondBit = xmlOutput.substring(ix)

    if (null == filePath)
    {
      println(firstBit)
      println("\n")
      println(comment)
      println(secondBit)
    }
    else
      Files.newBufferedWriter(Paths.get(filePath)).use { fOut ->
        fOut.write(firstBit)
        fOut.write("\n")
        fOut.write(comment)
        fOut.write(secondBit)
        fOut.write("")
      }
  }


  /****************************************************************************/
  /**
   * Promotes all the children of a given node to be siblings of the original
   * node (immediately following it).  This has the effect of turning the
   * original node into a self-closing node, whilst retaining the detail which
   * originally resided below it.
   *
   * @param parent Parent node.
   */

  fun promoteChildren (parent: Node)
  {
    val doc = parent.ownerDocument
    val children = parent.childNodes
    for (j in children.length - 1 downTo 0)
    {
      val child = children.item(j)
      val newNode = createNode(doc, child, true)
      insertNodeAfter(parent, newNode)
      deleteNode(child)
    }
  }


  /****************************************************************************/
  /**
   * Creates a new attribute or changes the value of an existing one.
   *
   * @param node Node.
   * @param attributeName Attribute name.
   * @param attributeValue Value.  (If null, attribute is deleted.)
   */

  fun setAttribute (node: Node, attributeName: String, attributeValue: String?)
  {
    if (null == attributeValue)
      deleteAttribute(node, attributeName)
    else
      (node as Element).setAttribute(attributeName, attributeValue)
  }


  /****************************************************************************/
  /**
   * Generates a printable representation of a node and its attributes.
   *
   * @param node Node.
   * @return String.
   */

  fun toString (node: Node): String
  {
    val nodeName = node.nodeName
    if ("#text" == nodeName) return "#text: " + node.textContent else if ("#comment" == nodeName) return "#comment: " + node.textContent

    var res = "$nodeName "
    var attributes = ""

    if (node.hasAttributes())
    {
      val attribs = getAttributes(node)
      for (k in attribs.keys) attributes += k + "='" + attribs[k] + "' "
    }

    if (attributes.isNotEmpty()) attributes = attributes.substring(0, attributes.length -1)
    res += attributes
    return "<$res>"
  }


  /****************************************************************************/
  private fun findNodesByAttributeValueInternal (parent: Node, nodeName: String, attributeName: String, value: String): List<Node>
  {
    val lcValue = value.lowercase()
    val xpath = makeXPathFactory().newXPath()
    val expr = xpath.compile(".//*[local-name()='$nodeName' and lower-case(@$attributeName) = '$lcValue']")
    return toListOfNodes(expr.evaluate(parent, XPathConstants.NODESET) as NodeList)
  }


  /****************************************************************************/
  private fun findNodesByAttributeValueInternal (parent: Node, nodeName: String, attributeName: String, value: Regex): List<Node>
  {
    val xpath = makeXPathFactory().newXPath()
    val expr = xpath.compile(".//*[local-name()='$nodeName'][@$attributeName and matches(@$attributeName, '$value', 'i')]")
    return toListOfNodes(expr.evaluate(parent, XPathConstants.NODESET) as NodeList)
  }


  /****************************************************************************/
  private fun findNodeByAttributeValueInternal (parent: Node, nodeName: String, attributeName: String, value: String): Node?
  {
    val lcValue = value.lowercase()
    val xpath = makeXPathFactory().newXPath()
    val expr = xpath.compile(".//*[local-name()='$nodeName' and lower-case(@$attributeName) = '$lcValue']") //*[local-name()='$nodeName'][@$attributeName and matches(@$attributeName, '$value', 'i')]")
    return expr.evaluate(parent, XPathConstants.NODE) as Node?
  }


  /****************************************************************************/
  private fun getAllNodesBelow(res: MutableList<Node>, startNode: Node) {
    val children = startNode.childNodes
    for (i in 0..< children.length) {
      val n = children.item(i)
      res.add(n)
      getAllNodesBelow(res, n)
    }
  }

  /****************************************************************************/
  private fun makeXPathFactory (): XPathFactory
  {
    return XPathFactory.newInstance(NamespaceConstant.OBJECT_MODEL_SAXON)
  }


  /****************************************************************************/
  /**
   * Converts a NodeList structure to List&lt;Node&gt;>.
   *
   * @param nl Node list to be converted.
   * @return Revised structure.
   */

  private fun toListOfNodes (nl: NodeList): List<Node>
  {
    val res: MutableList<Node> = ArrayList(nl.length)
    for (i in 0..< nl.length) res.add(nl.item(i))
    return res
  }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
object Dbg
{
  /****************************************************************************/
  /* The functions in this group can be used to pause processing based upon an
     examination of their outputs -- you simply need to apply a breakpoint to
     the println statement.  All of them return true if they hit the println
     statement. */

  /****************************************************************************/
  fun d (b: Boolean): Boolean
  {
    if (b)
      System.err.println("DEBUG")
    return b
  }


  /****************************************************************************/
  /* String equality, case-insensitive. */

  fun d (s1: String, s2: String): Boolean
  {
    val b = s1.equals(s2, ignoreCase = true)

    if (b)
      System.err.println("DEBUG")

    return b
  }


  /****************************************************************************/
  /* Parent string contains child string, case-insensitive. */

  fun dCont (parent:String, child: String): Boolean
  {
    val b = parent.lowercase().contains(child.lowercase())
    if (b)
      System.err.println("DEBUG")

    return b
  }


  /****************************************************************************/
  fun d (s: String) = println(s)


  /****************************************************************************/
  /**
   * Outputs contents of DOM.  Note that this uses a hard-coded output
   * location, and will therefore need changing for use on other systems.
   *
   * @param doc Document.
   * @param fileName Just the file name (not extension) for output.  Data is
   *                 written to the desktop.
   */

  fun outputDom (doc: Document, fileName: String = "a.xml")
  {
    Dom.outputDomAsXml(doc, Paths.get("C:/Users/Jamie/Desktop/" + fileName).toString(),null)
  }
}