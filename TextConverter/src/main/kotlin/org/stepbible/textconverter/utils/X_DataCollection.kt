package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.support.stepexception.StepExceptionShouldHaveBeenOverridden
import org.stepbible.textconverter.usxinputonly.Usx_BookAndChapterConverter
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.util.*
import javax.print.Doc
import kotlin.io.path.name

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

open class X_DataCollection constructor (fileProtocol: X_FileProtocol)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Returns an UNPOPULATED X_DataCollection of the same flavour as the present
   * one.
   *
   * @return X_DataCollection
   */

  fun makeDataCollectionOfThisFlavour () : X_DataCollection = X_DataCollection(m_FileProtocol)





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
  /**
  * Loads details a Document.  If the document contains multiple book nodes,
  * I assume that this determines book order.  Otherwise, book order is as
  * defined either by the metadata or by the UBS standard.  This replaces any
  * existing data and updates BibleStructure.
  *
  * @param doc
  */

  fun loadFromDoc (doc: Document)
  {
    withThisBibleStructure {
      clearAll()
      addFromDoc(doc)
      val rootNodes = doc.findNodesByName("book")
      if (1 != rootNodes.size)
        reloadBibleStructureFromRootNodes(false)
    }
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
  * @param saveText If true the text is saved.  Useful where it may need to
  *          be used for two different purposes.  Perhaps a good idea to
  *          call clearText when no longer needed.
  */

  fun loadFromFolder (folderPath: String, fileExtension: String, saveText: Boolean)
  {
    withThisBibleStructure {
      clearAll()
      val files = StepFileUtils.getMatchingFilesFromFolder(folderPath, ".*\\.$fileExtension".toRegex())
      val orderedBookList: MutableList<Int> = mutableListOf()
      files.forEach { Dbg.reportProgress("Loading ${it.name}."); orderedBookList.addAll(addFromFile(it.toString(), saveText)) }
      if (1 == files.size) sortStructuresByBookOrder(orderedBookList)
      reloadBibleStructureFromRootNodes(false)
    }
  }


  /****************************************************************************/
  /**
  * Populates the present instance from the *text* of another instance (or,
  * indeed, of this instance).  It clears all existing data and reloads
  * BibleStructure.
  *
  * @param text
  * @param saveText If true the text is saved.  Useful where it may need to
  *          be used for two different purposes.  Perhaps a good idea to
  *          call clearText when no longer needed.
  */

  fun loadFromText (text: String, saveText: Boolean)
  {
    withThisBibleStructure {
      BibleStructure.clear()
      restoreBookNumberToRootNodeMappings()
      // Don't clear the text here, in case it's actually coming from the present class.

      Dbg.reportProgress("Loading data from XML text.")
      val orderedBookList = addFromText(text, saveText)
      sortStructuresByBookOrder(orderedBookList)
      reloadBibleStructureFromRootNodes(false)
      if (saveText)
        setText(text)
    }
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
    BibleStructure.clear()
    restoreBookNumberToRootNodeMappings()
    clearText()
  }



  /****************************************************************************/
  /**
  * Can be used to release memory when saved text is no longer needed.
  */

  fun clearText () = m_Text.clear()



  /****************************************************************************/
  /**
  * Returns a list of the book numbers which are being processed.
  *
  * @return book numbers.
  */

  fun getBookNumbers () = m_BookNumberToRootNode.keys.toList()


  /****************************************************************************/
  /**
  * For use particularly with OSIS, where all books are in the same document.
  * Returns that document.
  *
  * @return Document.
  */

  fun getDocument (): Document
  {
    if (1 == getNumberOfDocuments())
      return m_BookNumberToRootNode.values.first { null != it }!!.ownerDocument
    else
      throw StepException("getDocument called where more than one document is available.")
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
  * Returns any saved text.
  *
  * @return Saved text.
  */

  fun getText () = m_Text.toString()


  /****************************************************************************/
  /**
  * By default, we do not bother to accumulate word counts.  For conversion-
  * time reversification, however, this is necessary, so this rescans the data
  * in order to acquire them.
  */

  fun loadWordCounts () = reloadBibleStructureFromRootNodes(true)



  /****************************************************************************/
  /**
  * Reloads the BibleStructure element -- for example after creating empty
  * verses to fill in blanks in the text.
  *
  * @param wantWordCount If true, accumulates word counts for all verses.
  */

  fun reloadBibleStructureFromRootNodes (wantWordCount: Boolean)
  {
    BibleStructure.clear()
    m_BookNumberToRootNode.filter { null != it.value }.forEach { BibleStructure.addFromBookRootNode("", it.value!!, wantWordCount = wantWordCount )}
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
  * Sets the text details.  Use with care, because this doesn't parse the
  * details into the BibleStructure member etc -- it gives you the
  * wherewithal to store the text, but doesn't set up any of the things which
  * would normally be based upon it.
  *
  * @param text
  */

  fun setText (text: String) { m_Text.clear(); m_Text.append(text) }





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

  fun debugDisplayBibleStructure () = withThisBibleStructure { BibleStructure.debugDisplayStructure() }





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
   * records details of the rest.
   */

  protected open fun filterOutUnwantedBooksAndPopulateRootNodesStructure (doc: Document): List<Int>
    = throw StepExceptionShouldHaveBeenOverridden()






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
  * @param saveText If true the text is saved.  Useful where it may need to
  *          be used for two different purposes.  Perhaps a good idea to
  *          call clearText when no longer needed.
  */

  private fun addFromFile (filePath: String, saveText: Boolean) = addFromText(File(filePath).readText(), saveText)


  /****************************************************************************/
  private fun addFromDoc (doc: Document): List<Int> = filterOutUnwantedBooksAndPopulateRootNodesStructure(doc)


  /****************************************************************************/
  /**
  * Adds details from a string.  It is permissible to call this multiple times
  * with different strings.  The effect is then cumulative.
  *
  * @param text
  * @param saveText If true the text is saved.  Useful where it may need to
  *          be used for two different purposes.  Perhaps a good idea to
  *          call clearText when no longer needed.
  * @return List of books added (may be empty if none have been selected for
  *   processing).
  */

  private fun addFromText (text: String, saveText: Boolean): List<Int>
  {
    val doc = Dom.getDocumentFromText(text, retainComments = true)
    val res = addFromDoc(doc)
    if (res.isNotEmpty() && saveText)
      m_Text.append(text)
    return res
  }


  /****************************************************************************/
  /* Restores the book-number-to-root-node mapping to its default state --
     either that defined by the configuration data, or the UBS ordering. */

  private fun restoreBookNumberToRootNodeMappings ()
  {
    m_BookNumberToRootNode.clear()
    ConfigData.getBookDescriptors().map { BibleBookNamesUsx.abbreviatedNameToNumber(it.ubsAbbreviation) }.forEach { m_BookNumberToRootNode[it] = null }
  }


  /****************************************************************************/
  /* In some cases, the book ordering is driven frm the content of the data
     files.  This arranges to have the internal data structures ordered
     correctly. */

  private fun sortStructuresByBookOrder (orderedBookList: List<Int>)
  {
    val newBookNumberToRootNode: MutableMap<Int, Node?> = mutableMapOf()
    orderedBookList.forEach { newBookNumberToRootNode[it] = m_BookNumberToRootNode[it]!! }
    m_BookNumberToRootNode = newBookNumberToRootNode
  }


  /****************************************************************************/
  /* Sets the RefBase.BibleStructure entry to the BibleStructure of the present
     X_DataCollection instance, does something, and then restores
     RefBase.BibleStructure. */

  private fun withThisBibleStructure (fn: () -> Unit)
  {
    val save: BibleStructure? = RefBase.getBibleStructure()
    RefBase.setBibleStructure(BibleStructure)
    fn()
    RefBase.setBibleStructure(save)
  }


  /****************************************************************************/
  lateinit var BibleStructure: BibleStructure
  protected var m_BookNumberToRootNode: MutableMap<Int, Node?> = mutableMapOf()
  protected val m_FileProtocol = fileProtocol
  private var m_Text = StringBuilder()



  /****************************************************************************/
  init {
    restoreBookNumberToRootNodeMappings()
  }
}





/******************************************************************************/
class Osis_DataCollection: X_DataCollection(Osis_FileProtocol)
{
  /****************************************************************************/
  override fun filterOutUnwantedBooksAndPopulateRootNodesStructure (doc: Document): List<Int>
  {
    val res: MutableList<Int> = mutableListOf()
    val nodeList = Dom.findNodesByAttributeValue(doc, "div", "type", "book").toSet() union Dom.findNodesByName(doc, "book")
    nodeList.forEach {
      if (Dbg.wantToProcessBookByAbbreviatedName(it["osisID"]!!))
      {
        val bookNo = BibleBookNamesOsis.abbreviatedNameToNumber(it["osisID"]!!)
        res.add(bookNo)
        m_BookNumberToRootNode[bookNo] = it
      }
      else
        Dom.deleteNode(it)
    }

    return res
  }



  /****************************************************************************/
  init {
    BibleStructure = BibleStructure(Osis_FileProtocol)
  }
}





/******************************************************************************/
class Usx_DataCollection: X_DataCollection(Usx_FileProtocol)
{
  /****************************************************************************/
  override fun filterOutUnwantedBooksAndPopulateRootNodesStructure (doc: Document): List<Int>
  {
    val res: MutableList<Int> = mutableListOf()

    Usx_BookAndChapterConverter.process(doc) // The material may need restructuring so that books and chapters are enclosing nodes.

    doc.findNodesByName("book").forEach {
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


  /****************************************************************************/
  init {
    BibleStructure = BibleStructure(Usx_FileProtocol)
  }
}
