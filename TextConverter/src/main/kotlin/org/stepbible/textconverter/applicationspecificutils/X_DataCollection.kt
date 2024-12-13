package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleStructure
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefBase
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File


/******************************************************************************/
/**
 * Gathers together various information about the data being processed.
 *
 * This caters for both USX and OSIS.
 *
 * Its main purpose is to allow me, as far as possible, to write code which is
 * agnostic as regards whether it is processing USX or OSIS -- it is a
 * container which bundles together in one place all of the relevant processors
 * to handle either.
 *
 * I assume here that we will be dealing *either* with a single file covering
 * all books (as is typically the case with OSIS) *or* with one file per
 * book (as is typically the case with USX).  I have done my best to hide this
 * fact, so that regardless of the form of input things will just work.  More
 * details appear below.  (I *don't* cater for a situation where we have more
 * than one file with some, but not all, containing more than one book.)
 *
 * Once the data has been read, you can no longer access it as though it were
 * a single document (which in fact it is not -- it is now a separate document
 * for each book, along with a template document which can be used to reassemble
 * the documents into a single document for external use.
 *
 * Instead, you access single books via [getRootNodes] or [getRootNode].  At
 * the end of processing, convertToDoc will give you back a full document.
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
  * Combines the place-holder document with the root nodes to create a new
  * document.
  *
  * @return New document.
  */

  fun convertToDoc (): Document
  {
    /**************************************************************************/
    /* This first part works with books which already existed in the document
       which was first read in, and replaces the book placeholders with the
       actual data we've been working on. */

    val bookNosStillToBeInsertedIntoDocument = m_BookNumberToRootNode.keys.filter { null != m_BookNumberToRootNode[it] }.toMutableSet()
    val newBookNodes = mutableMapOf<Int, Node>()
    val doc = Dom.cloneDocument(m_PlaceHolderDocument) // The document we are creating.
    m_FileProtocol.getBookNodes(doc).forEach { placeHolderBookNode -> // Run over the placeholder book nodes.
      val bookNo = m_FileProtocol.getBookNumber(placeHolderBookNode)
      val rootNode = m_BookNumberToRootNode[bookNo] // Get the working copy of the data for that book from the present class.
      bookNosStillToBeInsertedIntoDocument.remove(bookNo) // Flag the fact that we've processed this book.
      val replacementTargetBookNode = doc.importNode(rootNode, true) // Clone the working copy of the book into the document under construction.
      Dom.insertNodeBefore(placeHolderBookNode, replacementTargetBookNode) // Bung teh cloned copy into the document we are in the process of creating.
      Dom.deleteNode(placeHolderBookNode) // We don't need the placeholder any more.
      newBookNodes[m_FileProtocol.getBookNumber(replacementTargetBookNode)] = replacementTargetBookNode
    }



    /**************************************************************************/
    /* That's dealt with all available books, so there is nothing else to do. */

    if (bookNosStillToBeInsertedIntoDocument.isEmpty())
      return doc



    /**************************************************************************/
    /* Regrettably there is another possibility ... we may have created some
       new books in the course of the processing, and need to work out where
       we should put them. */

    val bookOrdering = BookOrdering.getOrder()
    bookNosStillToBeInsertedIntoDocument.sorted().forEach { bookNoToBeInserted -> // Run over the books still to be inserted.
      val ixLow = bookOrdering.indexOf(bookNoToBeInserted) + 1 // Should be able to rely upon the book ordering containing the book.
      for  (i in ixLow + 1 ..< bookOrdering.size) // Look over the following books in the ordering list
        if (bookOrdering[i] in newBookNodes) // We already have in the output document a later book, so we need to insert before that.
        {
          Dom.insertNodeBefore(newBookNodes[bookOrdering[i]]!!, getRootNode(bookNoToBeInserted)!!)
          return@forEach
        }

      doc.documentElement.appendChild(getRootNode(bookNoToBeInserted))!! // Nowhere to insert the book, so add it at the end.
    }



    /**************************************************************************/
    return doc
  }


  /****************************************************************************/
  /**
  * Populates the structure taking a single document as input.
  *
  * @param doc
  */

  fun loadFromDoc (doc: Document)
  {
    loadFromDocs(listOf(doc))
    createPlaceHolderDocument(doc)
  }


  /****************************************************************************/
  /* This creates a copy of the original document.  The book nodes in this
     document are actually meaningless -- I merely want them to _exist_ because
     they mark the places within the overall document where the books are
     situated.  I will replace them later by the revised versions which the
     processing creates. */

  private fun createPlaceHolderDocument (docIn: Document)
  {
    m_PlaceHolderDocument = Dom.cloneDocument(docIn)
    m_FileProtocol.getBookNodes(m_PlaceHolderDocument).forEach(Dom::deleteChildren) // We only need the book nodes themselves.
  }

  private lateinit var m_PlaceHolderDocument: Document


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
  * Populates the root nodes collection as a copy of the data in another
  * collection.
  *
  * @param otherCollection
  */

  fun loadFromRootNodes (otherCollection: X_DataCollection)
  {
    otherCollection.getRootNodes().forEach {
      m_BookNumberToRootNode[otherCollection.m_FileProtocol.getBookNumber(it)] = it.cloneNode(true)
    }

    m_PlaceHolderDocument = Dom.cloneDocument(otherCollection.m_PlaceHolderDocument)
  }


  /****************************************************************************/
  /**
  * Returns the BibleStructure data, first loading it if necessary.
  *
  * @return BibleStructure
  */

  fun getBibleStructure (wantCanonicalTextSize: Boolean = false): BibleStructure
  {
    if (!m_BibleStructureIsValid || (wantCanonicalTextSize && !m_BibleStructure.hasCanonicalTextSize()))
      reloadBibleStructureFromRootNodes(wantCanonicalTextSize)
   return m_BibleStructure
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
    return res ?: throw StepExceptionWithStackTraceAbandonRun("findPredecessorBook failed.")
  }


  /****************************************************************************/
  /**
  * Returns a list of the book numbers which are being processed.
  *
  * @return book numbers.
  */

  fun getBookNumbers () = m_BookNumberToRootNode.keys.filter { null != m_BookNumberToRootNode[it] && Dbg.wantToProcessBook(it) }


  /****************************************************************************/
  /**
  * Returns the file protocol handler for this text.
  */

  fun getFileProtocol () = m_FileProtocol



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
      Rpt.reportWithContinuation(level = 1, "Determining Bible structure ...") {
        val rootNodes = m_BookNumberToRootNode.values.filterNotNull()
        m_BibleStructure.addFromRootNodes(rootNodes, wantCanonicalTextSize)
      } // reportWithContinuation

      m_BibleStructureIsValid = true
    } // withThisBibleStructure


    //BibleBookNamesUsx.getAbbreviatedNameList().forEach { println(it + m_BibleStructure.bookExists(BibleBookNamesUsx.abbreviatedNameToNumber(it)))}
  } // fun


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
    /**************************************************************************/
    val bookNodes = m_FileProtocol.getBookNodes(docIn)
    val res = mutableListOf<Int>()



    /**************************************************************************/
    fun load ()
    {
      bookNodes.forEach { bookNode ->
        Rpt.reportBookAsContinuation(m_FileProtocol.getBookAbbreviation(bookNode))
        val docOut = Dom.createDocument()
        val importedNode = docOut.importNode(bookNode, true)
        val rootNode = docOut.appendChild(importedNode)
        val bookNo = m_FileProtocol.getBookNumber(rootNode)
        res.add(bookNo)
        synchronized(m_BookNumberToRootNode_Lock) { m_BookNumberToRootNode[bookNo] = rootNode }
      } // forEach
    } // fun



    /**************************************************************************/
    /* This excessively fiddly bit has no real functional purpose -- it just
       makes the progress reports look better.  If we're being called with a
       file containing a single book node, chances are we're going to be called
       with loads of other files, and ideally I'd avoid having the individual
       progress indicators for each book coming out on a separate line. */

    if (1 == bookNodes.size)
      load()
    else
      Rpt.reportWithContinuation(level = 1, "Loading data ...") {
        load()
      } // reportWithContinuation

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

  private fun addFromFile (filePath: String, preprocessor: ((String) -> String)) = addFromText(File(filePath).readText(), preprocessor)


  /****************************************************************************/
  /*
  * Adds details from a string.  It is permissible to call this multiple times
  * with different strings.  The effect is then cumulative.
  *
  * @param text
  * @return List of books added (may be empty if none have been selected for
  *   processing).
  */

  private fun addFromText (text: String, preprocessor: ((String) -> String)): List<Int>
  {
    var revisedText = preprocessor(text)
    revisedText = revisedText.replace("&lt;", "^lt;").replace("&gt;", "^gt;") // It's just too difficult to retain &lt etc as-is.
    val doc = Dom.getDocumentFromText(revisedText, retainComments = true)
    return addFromDoc(doc)
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
  val m_BookNumberToRootNode_Lock = Any()
  protected var m_BookNumberToRootNode: MutableMap<Int, Node?> = mutableMapOf()
  protected val m_FileProtocol = fileProtocol

  private val m_BibleStructure = BibleStructure(m_FileProtocol)
  private var m_BibleStructureIsValid = false



  /****************************************************************************/
  init {
    restoreBookNumberToRootNodeMappings()
  }
}





/******************************************************************************/
class Osis_DataCollection: X_DataCollection(Osis_FileProtocol)
class Usx_DataCollection: X_DataCollection(Usx_FileProtocol)


