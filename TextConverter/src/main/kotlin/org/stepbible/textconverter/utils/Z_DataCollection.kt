package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.support.stepexception.StepExceptionShouldHaveBeenOverridden
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.util.*

/******************************************************************************/
/**
 * Gathers together various information about the data being processed.
 *
 * This is a *slightly* uneasy mixture.  It caters for both OSIS and USX.  My
 * assumption is that USX will come as a separate file per book, while OSIS
 * will contain just a single covering all books.  I've tried to hide this
 * distinction, but am not sure of the extent to which I've succeeded.
 *
 * @author ARA "Jamie" Jamieson
 */

open class Z_DataCollection protected constructor (fileProtocol: Z_FileProtocol)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  enum class DataFormats { Usx, OsisPure, OsisExtended, OsisStepified, UNKNOWN }


  /****************************************************************************/
  /**
  * Adds details from a file.  It is permissible to call this multiple times
  * with different files.  The effect is then cumulative.
  *
  * @param filePath
  */

  fun addFromFile (filePath: String, saveText: Boolean) = addFromText(File(filePath).readText(), saveText)


  /****************************************************************************/
  /**
  * Adds details from a string.  It is permissible to call this multiple times
  * with different strings.  The effect is then cumulative.
  *
  * @param text
  * @param saveText If true the text is saved.  Useful where it may need to
  *          be used for two different purporses.  Perhaps a good idea to
  *          call clearText when no longer needed.
  */

  fun addFromText (text: String, saveText: Boolean)
  {
    val doc = Dom.getDocumentFromText(text, retainComments = true)
    filterOutUnwantedBooks(doc)
    BibleStructure.addFromDom("", doc, wantWordCount = true)
    addFromDoc(doc)
    if (saveText) m_Text = text
  }


  /****************************************************************************/
  /**
  * Can be used to release memory when saved text is no longer needed.
  */

  fun clearText () { m_Text = "" }



  /****************************************************************************/
  /**
  * Returns a list of the book numbers which are being processed.
  *
  * @return book numbers.
  */

  fun getBookNumbers () = m_BookNumberToRootNode.keys.toList()


  /****************************************************************************/
  /**
  * For use particularly with OSIS, where all books are in the same Document.
  * Returns that document.
  *
  * @return Document.
  */

  fun getDocument (): Document = m_BookNumberToRootNode.values.first { null != it }!!.ownerDocument


  /****************************************************************************/
  /**
  * Returns the document associated with a given book.  Note that as far as the
  * present class is concerned, different books may appear in different
  * documents, or all in the same document.
  *
  * @param bookName
  * @return Document.
  */

  fun getDocument (bookName: String) = m_BookNumberToRootNode[getIndex(bookName)]!!.ownerDocument


  /****************************************************************************/
  /**
  * Returns the document associated with a given book.  Note that as far as the
  * present class is concerned, different books may appear in different
  * documents, or all in the same document.
  *
  * @param bookNo
  * @return Document.
  */

  fun getDocument (bookNo: Int) = m_BookNumberToRootNode[bookNo]!!.ownerDocument


  /****************************************************************************/
  /**
  * Returns the root node associated with a given book.  Note that as far as
  * the present class is concerned, different books may appear in different
  * documents, or all in the same document.
  *
  * @param bookName
  * @return Root node.
  */

  fun getRootNode (bookName: String) = m_BookNumberToRootNode[getIndex(bookName)]!!


  /****************************************************************************/
  /**
  * Returns the root node associated with a given book.  Note that as far as
  * the present class is concerned, different books may appear in different
  * documents, or all in the same document.
  *
  * @param bookNo
  * @return Root node.
  */

  fun getRootNode (bookNo: Int) = m_BookNumberToRootNode[bookNo]


  /****************************************************************************/
  /**
  * Returns a list of all the root nodes for the given input.
  *
  * @return Root nodes.
  */

  fun getRootNodes (): List<Node>
  {
    return m_BookNumberToRootNode.map {
      if (Dbg.wantToProcessBook(it.key)) it.value else null } .filterNotNull()
  }


  /****************************************************************************/
  /**
  * Returns any saved text.
  *
  * @return Saved text.
  */

  fun getText () = m_Text


  /****************************************************************************/
  /**
  * Reloads the BibleStructure element -- for example after creating empty
  * verses to fill in blanks in the text.
  */

  fun reloadBibleStructure ()
  {
    m_BookNumberToRootNode.filter { null != it.value }.forEach { BibleStructure.addFromDom("", it.value!!.ownerDocument, wantWordCount = true )}
  }


  /****************************************************************************/
  /**
  * Removes a single book.
  *
  * @param bookNo
  */

  fun removeBook (bookNo: Int) = m_BookNumberToRootNode.remove(bookNo)


  /****************************************************************************/
  /**
  * Records the data format.  No need to call if you are using this to
  * represent USX via Usx_DataCollection, but you will need to do so if
  * using it for any flavour of OSIS.  In that latter case, we need to know
  * (for debug and defensive programming purposes) whether the content
  * represents Pure OSIS, Extended OSIS or Stepified OSIS -- see description
  * of DataFormat for more details.
  *
  * @param format Required setting.
  */

  fun recordDataFormat (format: DataFormats) { DataFormat = format }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Protected                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  open fun addFromDoc (doc: Document): Unit = throw StepExceptionShouldHaveBeenOverridden()


  /***************************************************************************/
  /**
   * Gets rid of any books which we have decided to exclude on this run.
   */

  protected open fun filterOutUnwantedBooks (doc: Document): Unit = throw StepExceptionShouldHaveBeenOverridden()


  /****************************************************************************/
  protected fun addNameToBookMapping (bookName: String, rootNode: Node)
  {
    m_BookNumberToRootNode[getIndex(bookName)] = rootNode
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun getIndex (externalName: String): Int
  {
    var res = -1
    try { res = BibleBookNamesOsis.nameToNumber(externalName) }
    catch (_: Exception)
    {
      try { res = BibleBookNamesUsx.nameToNumber(externalName) }
      catch (_: Exception) {}
    }

    if (-1 == res) throw StepException("Unknown bookname: $externalName.")
    return res
  }


  /****************************************************************************/
  lateinit var BibleStructure: Z_BibleStructure
  protected val m_BookNumberToRootNode = TreeMap<Int, Node?>()
  protected val m_FileProtocol = fileProtocol
  private lateinit var m_Text: String
  var DataFormat: DataFormats

  init {
    DataFormat = DataFormats.UNKNOWN
  }
}





/******************************************************************************/
class Osis_DataCollection: Z_DataCollection(Osis_FileProtocol)
{
  /****************************************************************************/
  override fun filterOutUnwantedBooks (doc: Document)
  {
    val nodeList = Dom.findNodesByAttributeValue(doc, "div", "type", "book").toSet() union Dom.findNodesByName(doc, "book")
    nodeList.filterNot { Dbg.wantToProcessBookByAbbreviatedName(it["osisID"]!!) }.forEach { Dom.deleteNode(it) }
  }


  /****************************************************************************/
  override fun addFromDoc (doc: Document)
  {
    Dom.getNodesInTree(doc).forEach {
      val nodeName = Dom.getNodeName(it)
      if ("book" == nodeName || ("div" == nodeName && "book" == it["type"]!!.lowercase()))
        addNameToBookMapping(it["osisID"]!!, it)
    }
  }


  /****************************************************************************/
  init {
    recordDataFormat(DataFormats.OsisExtended)
    BibleStructure = Osis_BibleStructure()
  }
}





/******************************************************************************/
class Usx_DataCollection: Z_DataCollection(Usx_FileProtocol)
{
  /****************************************************************************/
  override fun addFromDoc (doc: Document) = Dom.findNodesByName(doc, "book").forEach { addNameToBookMapping(it["code"]!!, it) }


  /****************************************************************************/
  /* I assume that for USX, we have multiple files, one per book. */

  override fun filterOutUnwantedBooks (doc: Document)
  {
    Dom.findNodesByName(doc, "book")
      .filterNot { Dbg.wantToProcessBookByAbbreviatedName(it["code"]!!) }
      .forEach { Dom.deleteNode(it) }
  }


  /****************************************************************************/
  init {
    recordDataFormat(DataFormats.Usx)
    BibleStructure = Usx_BibleStructure()
  }
}
