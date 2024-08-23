package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.usxinputonly.Usx_BookAndChapterConverter
import org.stepbible.textconverter.usxinputonly.Usx_Preprocessor
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.util.*

/******************************************************************************/
/**
 * Gathers together various information about the data being processed.
 *
 * This caters for both USX and OSIS.  (It also caters for the OSIS generated
 * from VL, but not for the VL data itself.)
 *
 * I assume here that we will be dealing *either* with a single file covering
 * all books (as is typically the case with VL and OSIS) *or* with one file per
 * book (as is typically the case with USX).  I have done my best to hide this
 * fact, so that regardless of the form of input things will just work.  More
 * details appear below.  (I *don't* cater for a situation where we have more
 * than one file some of which contain more than one book.)
 *
 * That constitutes one potential complication.  A second one arises because
 * we may need to cope with non-standard book orderings.
 *
 *
 *
 *
 *
 * ## Non-standard book orderings
 *
 * The ordering information is held internally in a data structure
 * (m_BookNumberToRootNode) which maps UBS book numbers to the book nodes for
 * those books.
 *
 * If there is a book list for the text (either in the STEP configuration data
 * or in externally supplied metadata such as that from DBL), that determines
 * the ordering.  If there is no available book list, ordering is assumed to
 * follow the standard UBS scheme.
 *
 * Except in both cases, if all input is being taken from a single file, that
 * trumps everything else.
 *
 * Where m_BookNumberToRootNode is based upon the UBS scheme, it will have
 * a full list of entries, although some of them may be empty if there is no
 * text for the book.  Where it is based upon metadata, it will have entries
 * only for those books mentioned in the metadata (and again, some of them
 * may be empty).  Where this is overridden by the content of the data, it
 * will follow that.
 *
 *
 *
 *
 *
 * ## File-per-book and single file: hiding the differences
 *
 * When reading data, I recommend not structuring your code to work with
 * individual documents (all of which are, in fact available should you
 * *really* need them).  Rather, iterate over the book nodes using
 *
 * The best way to approach matters without worrying about this structure
 * is to run over the book root nodes -- the
 *
 * @author ARA "Jamie" Jamieson
 */

open class X_DataCollection (fileProtocol: X_FileProtocol)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Load data                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* The various load* methods have the following in common:

     - They empty out the local data structures and BibleStructure (and thus
       replace any existing data).

     - They set RefBase.BibleStructure to the BibleStructure entry of the
       current instance of X_DataCollection while carrying out their processing,
       and then restore it to its previous value when complete.

     - On completion, the local data structures and BibleStructure will all be
       populated.  The latter will not contain verse word counts.  If you need
       those, use reloadBibleStructureFromRootNodes(true).
  */
  /****************************************************************************/

  /****************************************************************************/
  fun addFromDoc (docIn: Document): List<Int>
  {
    val res = filterOutUnwantedBooksAndPopulateRootNodesStructure(docIn)
    return res
  }


  /****************************************************************************/
  /**
  * Copies the ProcessRegistry details from another instance.
  *
  * @param otherDataCollection
  */

  fun copyProcessRegistryFrom (otherDataCollection: X_DataCollection)
  {
    getProcessRegistry().setDoneDetails(otherDataCollection.getProcessRegistry())
  }


  /****************************************************************************/
  /**
   * Loads details from a collection of one or more documents.
   *
   * @param docs
   */

  fun loadFromDocs (docs: List<Document>)
  {
    withThisBibleStructure {
      clearAll()
      docs.forEach(::addFromDoc)
    }
  }


  /****************************************************************************/
  /**
  * Returns the BibleStructure data, first loading it if necessary.
  *
  * @return BibleStructure
  */

  fun getBibleStructure (): BibleStructure
  {
    if (!m_BibleStructureIsValid)
      reloadBibleStructureFromRootNodes(false)
   return m_BibleStructure
  }


  /****************************************************************************/
  /**
  * Loads details from all files in a given folder.  If the folder contains only
  * a single file, I assume that the content of that file determines book order.
  * Otherwise, book order is as defined either by the metadata or by the UBS
  * standard.  This replaces any existing data and updates BibleStructure.
  *
  * @param folderPath
  * @param fileExtension Extension used to select files from folder.
  */

  fun loadFromFolder (folderPath: String, fileExtension: String)
  {
    withThisBibleStructure {
      clearAll()
      val files = StepFileUtils.getMatchingFilesFromFolder(folderPath, ".*\\.$fileExtension".toRegex())
      val orderedBookList: MutableList<Int> = mutableListOf()
      files.forEach { orderedBookList.addAll(addFromFile(it.toString())) }
      sortStructuresByBookOrder(orderedBookList)
      //reloadBibleStructureFromRootNodes(false)
    }
  }


  /****************************************************************************/
  /**
  * Populates the present instance from the *text* of another instance (or,
  * indeed, of this instance).  It clears all existing data and reloads
  * BibleStructure.
  *
  * @param text
  */

  fun loadFromText (text: String)
  {
    Dbg.reportProgress("Loading data from XML text.")

    withThisBibleStructure { // Don't clear the text here, in case it's actually coming from the present class.
      restoreBookNumberToRootNodeMappings()
      val bookList = addFromText(text)
      sortStructuresByBookOrder(bookList)
    }
  }


  /****************************************************************************/
  /**
  * Replaces the details of a single document.  This is intended mainly for
  * use when carrying out XSLT-based tweaks to an input document which is not
  * fully USX-conformant.  I am assuming, at the very least, that it will be
  * called early, so that nothing much will be dependent upon the previous
  * version of the document, since I do nothing here other than overwrite the
  * previous version.
  *
  * @param doc
  */

  fun replaceDocumentStructure (doc: Document)
  {
    addFromDoc(doc)
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Utilities                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Can be used to release memory when stuff is no longer needed.
  */

  fun clearAll ()
  {
    clearBibleStructure()
    restoreBookNumberToRootNodeMappings()
  }



  /****************************************************************************/
  /**
  * Finds the book number of the book in the internal structures which
  * precedes a given book.  Useful in reversification where we may need to
  * create a new book and insert it at the right place in the document.
  *
  * Note that I throw an exception if I fail to find a location.  Strictly this
  * is wrong -- if we want to insert Genesis, then that would preclude doing so.
  * But at least in terms of reversification, we never _are_ going to want to
  * insert Genesis.
  *
  * @param bookNo Book number we want to insert.
  * @return Number of preceding book.
  */

  fun findPredecessorBook (bookNo: Int): Int
  {
    val res = m_BookNumberToRootNode.keys.reversed().find { it < bookNo && null != getRootNode(it) }
    return res ?: throw StepException("findPredecessorBook failed.")
  }

  /****************************************************************************/
  /**
  * Returns a list of the book numbers which are being processed.
  *
  * @return book numbers.
  */

  fun getBookNumbers () = m_BookNumberToRootNode.keys.filter { Dbg.wantToProcessBook(it) }


  /****************************************************************************/
  /**
  * For use particularly with OSIS, where all books are in the same document.
  * Returns that document.
  *
  * @return Document.
  */

  fun getDocument (): Document
  {
    when (getNumberOfDocuments())
    {
       0    -> throw StepException("getDocument called where no document is available.")
       1    -> return m_BookNumberToRootNode.values.first { null != it }!!.ownerDocument
       else -> throw StepException("getDocument called where more than one document is available.")
    }
  }


  /****************************************************************************/
  /**
  * Returns the file protocol handler for this text.
  */

  fun getFileProtocol () = m_FileProtocol



  /****************************************************************************/
  /**
  * Returns a count of the number of separate documents which have fed into the
  * processing.  I do rather make the assumption here that either each book will
  * have its own file, or else they'll all be in a single file: I'm assuming
  * we're not going to get, say, two in one file and three in another.
  *
  * @return Number of documents.
  */

  fun getNumberOfDocuments (): Int
  {
    val nBooks = m_BookNumberToRootNode.values.filterNotNull().count()
    val docs = IdentityHashMap<Any, Int>(); m_BookNumberToRootNode.values.filterNotNull().forEach { docs[it.ownerDocument] = 0 }
    val nDocs = docs.count()
    if (nBooks != nDocs && 1 != nDocs) throw StepException("USX input has neither all books in a single file nor a separate file per book.")
    return nDocs
  }


  /****************************************************************************/
  /**
  * Returns the root node associated with a given book.  Note that as far as
  * the present class is concerned, different books may appear in different
  * documents, or all in the same document -- it makes no difference.
  *
  * @param bookNo
  * @return Root node.
  */

  fun getRootNode (bookNo: Int) = m_BookNumberToRootNode[bookNo]


  /****************************************************************************/
  /**
  * Returns a list of all the selected root nodes.
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
  * Returns a list of all documents for the given text.  Note that I make no
  * guarantee as regards ordering.
  *
  * @return Documents.
  */

  fun getDocuments (): List<Document>
  {
    val docs = IdentityHashMap<Document, Int>()
    val docsInOrder: MutableList<Document> = mutableListOf()
    getRootNodes().forEach { val doc = it.ownerDocument; if (doc !in docs) docs[doc] = 0; docsInOrder.add(doc) }
    return docsInOrder.toList()
  }


  /****************************************************************************/
  /**
  * Returns the process registry for this collection of data.
  *
  * @return Process registry.
  */

  fun getProcessRegistry () = m_ProcessRegistry



  /****************************************************************************/
  /**
  * Flags the BibleStructure element as invalid.
  */

  fun invalidateBibleStructure ()
  {
    clearBibleStructure()
    RefBase.setBibleStructure(null)
  }


  /****************************************************************************/
  /**
  * Reloads the BibleStructure element -- for example after creating empty
  * verses to fill in blanks in the text.
  *
  * @param wantCanonicalTextSize If true, accumulates word counts for all verses.
  */

  fun reloadBibleStructureFromRootNodes (wantCanonicalTextSize: Boolean)
  {
    clearBibleStructure()
    withThisBibleStructure {
      RefBase.setBibleStructure(m_BibleStructure)
      m_BookNumberToRootNode.filter { null != it.value }.forEach { m_BibleStructure.addFromBookRootNode("", it.value!!, wantCanonicalTextSize = wantCanonicalTextSize )}
      m_BibleStructureIsValid = true
    }
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
  * Sets the root node for a given book.
  *
  * @param bookNo
  * @param rootNode
  */

  fun setRootNode (bookNo: Int, rootNode: Node?)
  {
    m_BookNumberToRootNode[bookNo] = rootNode
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Debug                                    **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Displays the content of the BibleStructure.
  */

  fun debugDisplayBibleStructure () = withThisBibleStructure { getBibleStructure().debugDisplayStructure() }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Protected                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /***************************************************************************/
  /**
   * Gets rid of any books which we have decided to exclude on this run and
   * records details of the rest.  Previously this root version was a dummy
   * which simply threw an exception.  On balance, I think I need it to be
   * a dummy only in the sense that it selects absolutely everything for
   * processing.
   *
   * @param docIn
   *
   * @return List of book numbers to be processed.
   */

  protected open fun filterOutUnwantedBooksAndPopulateRootNodesStructure (docIn: Document): List<Int>
   {
     val docOut = Dom.createDocument()
     val importedNode = docOut.importNode(docIn.documentElement, true)
     docOut.appendChild(importedNode)

     val res: MutableList<Int> = mutableListOf()
     val nodeList = Dom.findNodesByAttributeValue(docOut, "div", "type", "book").toSet() union Dom.findNodesByName(docOut, "book")
     nodeList.forEach {
         Dbg.reportProgress("- Loading data for ${it["osisID"]!!}.")
         val bookNo = BibleBookNamesOsis.abbreviatedNameToNumber(it["osisID"]!!)
         res.add(bookNo)
         setRootNode(bookNo, it)
     }

     return res
   }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Adds details from a file.  It is permissible to call this multiple times
  * with different files.  The effect is then cumulative.
  *
  * @param filePath
  */

  private fun addFromFile (filePath: String) = addFromText(File(filePath).readText())


  /****************************************************************************/
  /**
  * Adds details from a string.  It is permissible to call this multiple times
  * with different strings.  The effect is then cumulative.
  *
  * @param text
  * @return List of books added (may be empty if none have been selected for
  *   processing).
  */

  private fun addFromText (text: String): List<Int>
  {
    var revisedText = if (X_FileProtocol.ProtocolType.USX == m_FileProtocol.Type) Usx_Preprocessor.processRegex(text) else text
    revisedText = revisedText.replace("&lt;", "^lt;").replace("&gt;", "^gt;") // It's just too difficult to retain &lt etc as-is.
    val doc = Dom.getDocumentFromText(revisedText, retainComments = true)
    return addFromDoc(doc)
  }

  private fun x (text: String)
  {
    val res: MutableList<String> = mutableListOf()
    val C_Pat = Regex("\"(Bible[^:]+?:[^\"]+?)\"")
    val m = C_Pat.findAll(text)
    m.forEach {
      Dbg.d("+++" + it.groups[1]!!.value)
    }

  }


  /****************************************************************************/
  private fun clearBibleStructure ()
  {
    m_BibleStructure.clear()
    m_BibleStructureIsValid = false
  }


  /****************************************************************************/
  /* Restores the book-number-to-root-node mapping to its default state --
     either that defined by the configuration data, or the UBS ordering. */

  private fun restoreBookNumberToRootNodeMappings ()
  {
    m_BookNumberToRootNode.clear()
    ConfigData.getBookDescriptors()
      .filter { it.ubsAbbreviation.isNotEmpty() }
      .map { BibleBookNamesUsx.abbreviatedNameToNumber(it.ubsAbbreviation) }
      .forEach { setRootNode(it, null) }
  }


  /****************************************************************************/
  /* In some cases, the book ordering is driven from the content of the data
     files.  This arranges to have the internal data structures ordered
     correctly. */

  private fun sortStructuresByBookOrder (bookList: List<Int>)
  {
    val newBookNumberToRootNode: MutableMap<Int, Node?> = mutableMapOf()
    bookList.forEach { newBookNumberToRootNode[it] = getRootNode(it) }
    m_BookNumberToRootNode = newBookNumberToRootNode.toSortedMap()
  }


  /****************************************************************************/
  /* Sets the RefBase.BibleStructure entry to the BibleStructure of the present
     X_DataCollection instance, does something, and then restores
     RefBase.BibleStructure. */

  private fun withThisBibleStructure (fn: () -> Unit)
  {
    val save: BibleStructure? = RefBase.getBibleStructure()
    RefBase.setBibleStructure(m_BibleStructure)
    fn()
    RefBase.setBibleStructure(save)
  }


  /****************************************************************************/
  protected var m_BookNumberToRootNode: MutableMap<Int, Node?> = mutableMapOf()
  protected val m_FileProtocol = fileProtocol

  private val m_BibleStructure = BibleStructure(m_FileProtocol)
  private var m_BibleStructureIsValid = false
  private var m_ProcessRegistry = ProcessRegistry()



  /****************************************************************************/
  init {
    restoreBookNumberToRootNodeMappings()
  }
}





/******************************************************************************/
class Osis_DataCollection: X_DataCollection(Osis_FileProtocol)
{
  /****************************************************************************/
  override fun filterOutUnwantedBooksAndPopulateRootNodesStructure (docIn: Document): List<Int>
  {
    //Dbg.d(docIn)
    val docOut = Dom.createDocument()
    val importedNode = docOut.importNode(docIn.documentElement, true)
    docOut.appendChild(importedNode)

    //--------------------------------------------------
    //val nodeList = Dom.findNodesByName(docIn, "div").filter { "book" == it["type"] }.toSet() union Dom.findNodesByName(docOut, "book")
    //Dbg.d(docIn, "in.xml")
    //Dbg.d(docOut, "out.xml")
    //val nodeListIn = Dom.findNodesByAttributeValue(docIn, "div", "type", "book")
    //val nodeListOut = Dom.findNodesByAttributeValue(docOut, "div", "type", "book")
    //--------------------------------------------------


    val res: MutableList<Int> = mutableListOf()
    val nodeList = Dom.findNodesByAttributeValue(docOut, "div", "type", "book").toSet() union Dom.findNodesByName(docOut, "book") // Worryingly, not sure why I need the above instead.
    nodeList.forEach {
      if (Dbg.wantToProcessBookByAbbreviatedName(it["osisID"]!!))
      {
        val bookNo = BibleBookNamesOsis.abbreviatedNameToNumber(it["osisID"]!!)
        res.add(bookNo)
        setRootNode(bookNo, it)
      }
      else
        Dom.deleteNode(it)
    }

    return res
  }
}





/******************************************************************************/
class Usx_DataCollection: X_DataCollection(Usx_FileProtocol)
{
  /****************************************************************************/
  override fun filterOutUnwantedBooksAndPopulateRootNodesStructure (docIn: Document): List<Int>
  {
    val docOut = Dom.createDocument()
    val importedNode = docOut.importNode(docIn.documentElement, true)
    docOut.appendChild(importedNode)
//    Dbg.d(docIn)
//    Dbg.d(docOut)

    val res: MutableList<Int> = mutableListOf()

    Usx_BookAndChapterConverter.process(docOut) // The material may need restructuring so that books and chapters are enclosing nodes.
    docOut.findNodesByName("book").forEach {
      if (Dbg.wantToProcessBookByAbbreviatedName(it["code"]!!))
      {
        val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(it["code"]!!)
        res.add(bookNo)
        m_BookNumberToRootNode[bookNo] = it
      }
      else
        Dom.deleteNode(it)
    }

    return res
  }
}

