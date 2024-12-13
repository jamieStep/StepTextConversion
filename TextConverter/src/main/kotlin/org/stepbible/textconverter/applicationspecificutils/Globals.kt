package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.w3c.dom.Document
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/****************************************************************************/
/**
 * Shared globals.
 *
 * @author ARA "Jamie" Jamieson
 */

object Globals: ObjectInterface
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
  * Used to record the fact that I have had to change book names.  At present
  * this is only an issue with Greek Esther, where I may receive either the
  * full text or the additions text and want to allow for the possibility that
  * they have been misidentified in the text (ie the additions named as though
  * they were the full text or vice versa).
  *
  * Given that there is presently only this one possible renaming, using a
  * map to hold the details is definitely overfill, but I want to allow for
  * the possibility of handling additions to Daniel at some point, just in
  * case.  (This isn't actually an issue at present so far as I can see,
  * because things don't seem to cater properly for the two flavours of Greek
  * Daniel.)
  *
  * This map is keyed on the revised book number and give the book number
  * as received in the incoming text. */

  val BookNamesRevisions = ConcurrentHashMap<Int, Int>()


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

  fun getUniqueExternal () = m_UniqueExternalId.incrementAndGet()


  /****************************************************************************/
  /**
  * Returns a counter which can be used to generate unique ids.  This counter
  * is intended for applications where the value is required only during
  * processing, and will therefore not be apparent to human readers once
  * processing is complete.
  *
  * @return Counter
  */

  fun getUniqueInternalId () = m_UniqueInternalId.incrementAndGet()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /**************************************************************************/
  private var m_UniqueExternalId = AtomicInteger(0)
  private var m_UniqueInternalId = AtomicInteger(0)
}


/******************************************************************************/
val InternalOsisDataCollection by lazy { Osis_DataCollection() }
val UsxDataCollection by lazy { Usx_DataCollection() }
lateinit var ExternalOsisDoc: Document

@JvmInline value class Original<T> (val value: T)
@JvmInline value class Revised<T>  (val value: T)