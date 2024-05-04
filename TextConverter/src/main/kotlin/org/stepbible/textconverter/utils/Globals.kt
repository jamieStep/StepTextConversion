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
  * for use in OSIS references.  This counter is intended for applications
  * where the value will continue to be visible to human readers at the end
  * of processing, and where a hole in the numbering scheme might otherwise
  * cause consternation as to whether something has gone wrong.
  *
  * @return Counter
  */

  fun getUniqueExternal () = ++m_UniqueExternalId


  /****************************************************************************/
  /**
  * Returns a counter which can be used to generate unique ids.  This counter
  * is intended for applications where the value is required only during
  * processing, and will therefore not be apparent to human readers once
  * processing is complete.
  *
  * @return Counter
  */

  fun getUniqueInternalId () = ++m_UniqueInternalId





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /**************************************************************************/
  private var m_UniqueExternalId = 0
  private var m_UniqueInternalId = 0
}


/******************************************************************************/
var ExternalOsisDataCollection = Osis_DataCollection()
var InternalOsisDataCollection = Osis_DataCollection()
var UsxDataCollection = Usx_DataCollection()
lateinit var Phase1TextOutput: String
