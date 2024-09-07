package org.stepbible.textconverter.applicationspecificutils

import org.w3c.dom.Document

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
val InternalOsisDataCollection by lazy { Osis_DataCollection() }
val UsxDataCollection by lazy { Usx_DataCollection() }
lateinit var ExternalOsisDoc: Document
