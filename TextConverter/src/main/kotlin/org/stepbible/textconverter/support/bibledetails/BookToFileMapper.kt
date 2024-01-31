package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.get
import org.w3c.dom.Document

abstract class BookToFileMapper
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Package                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Returns an ordered list of (UsxBookAbbreviation, FilePath) pairs.
  *
  * @return List.
  */

  fun getFileList () = m_AbbreviationToFileMap.keys.map { Pair(it, m_AbbreviationToFileMap[it]!! ) }


  /****************************************************************************/
  /**
  * Returns an ordered list of (UsxBookAbbreviation, FilePath) pairs for just
  * those books selected in Dbg for processing.
  *
  * @return List.
  */

  fun getSelectedFileList () = m_AbbreviationToFileMap.keys.filter{ Dbg.wantToProcessBookByAbbreviatedName(it) }.map { Pair(it, m_AbbreviationToFileMap[it]!! ) }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  protected abstract fun getBookCode (doc:Document): String



  /****************************************************************************/
  protected fun recordFiles (filePaths: List<String>)
  {
    val map = mutableMapOf<String, String>()
    filePaths.forEach { map[getBookCode(Dom.getDocument(it))] = it }
    val orderedKeys = map.keys.sortedBy { BibleBookNamesUsx.abbreviatedNameToNumber(it) }
    orderedKeys.forEach { m_AbbreviationToFileMap[it] = map[it]!! }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private var m_AbbreviationToFileMap = mutableMapOf<String, String>()
}





/******************************************************************************/
/**
* A class to handle raw USX files (ie where the book name is held in the code
* attribute of the book node).
*/

class BookToFileMapperRawUsx (folderPath: String, fileExtension: String): BookToFileMapper()
{
  override fun getBookCode (doc: Document) = Dom.findNodeByName(doc, "book")!!["code"]!!.uppercase()

  init { recordFiles(StepFileUtils.getMatchingFilesFromFolder(folderPath, ".*\\.$fileExtension".toRegex()).map { it.toString() }) }
}