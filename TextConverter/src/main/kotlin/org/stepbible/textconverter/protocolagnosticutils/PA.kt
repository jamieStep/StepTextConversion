package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleStructure
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.applicationspecificutils.X_FileProtocol

/******************************************************************************/
/**
* Common base class for protocol-agnostic utilities.
*
* @author ARA "Jamie" Jamieson
*/

open class PA
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
  * Extracts common information used by most of the inheriting classes.
  *
  * @param dataCollection The data collection being processed.
  */

  protected fun extractCommonInformation (dataCollection: X_DataCollection, wantBibleStructure: Boolean = false)
  {
    if (wantBibleStructure) m_BibleStructure = dataCollection.getBibleStructure()
    m_DataCollection = dataCollection
    m_FileProtocol = dataCollection.getFileProtocol()
    m_EmptyVerseHandler = m_FileProtocol.getEmptyVerseHandler()
  }


  /****************************************************************************/
  protected var m_BibleStructure: BibleStructure? = null
  protected lateinit var m_DataCollection: X_DataCollection
  protected lateinit var m_EmptyVerseHandler: PA_EmptyVerseHandler
  protected lateinit var m_FileProtocol: X_FileProtocol
}