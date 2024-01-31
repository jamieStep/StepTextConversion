package org.stepbible.textconverter.utils

/****************************************************************************/
/**
 * Shared globals.
 *
 * @author ARA "Jamie" Jamieson
 */

object Globals
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
  * Returns a counter which can be used to generate unique ids, for example
  * for use in OSIS references.
  *
  * @return Counter
  */

  fun getUniqueIdCounter () = ++m_UniqueIdCounter





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /**************************************************************************/
  private var m_UniqueIdCounter = 0
}


/******************************************************************************/
var OsisPhase1OutputDataCollection = Osis_DataCollection()
//var OsisPhase2InputCollection = Osis_DataCollection()
var OsisPhase2SavedDataCollection = Osis_DataCollection()
var OsisTempDataCollection = Osis_DataCollection()
var UsxDataCollection = Usx_DataCollection()

lateinit var DataCollection: Z_DataCollection
lateinit var IssueAndInformationRecorder: Z_IssueAndInformationRecorder


