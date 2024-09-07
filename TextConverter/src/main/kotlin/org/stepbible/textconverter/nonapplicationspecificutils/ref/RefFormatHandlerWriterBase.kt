package org.stepbible.textconverter.nonapplicationspecificutils.ref

/****************************************************************************/
/**
 * A base class from which element writers inherit.
 *
 * @author ARA "Jamie" Jamieson
 */

 abstract class RefFormatHandlerWriterBase
 {
  /****************************************************************************/
  /**
   *  Converts an element of a reference collection to string form.
   *
   *  @param rcp The thing to be converted.
   *  @param] format Forces a particular format.
   *  @param context Supplies contextual information used in order to omit
   *   elements of the output which can be deduced from context.
   *  @return String representation.
   */

  fun toString (rcp: RefCollectionPart, format: String?, context: Ref?): String
  {
    return if (rcp is Ref) toString(rcp, format, context) else toString(rcp as RefRange, format, context)
  }


  /****************************************************************************/
  /**
  * Converts a single reference to string form.
  *
  * @param ref Reference to be handled.
  * @param format Optional format string.
  * @param context Optional Ref giving context information.
  */

  abstract fun toString (ref: Ref, format: String? = null, context: Ref? = null): String


  /****************************************************************************/
  /**
  * Converts a reference range to string form.
  *
  * @param refRange Item to be handled.
  * @param format Optional format string.
  * @param context Optional Ref giving context information.
  */

  abstract fun toString (refRange: RefRange, format: String? = null, context: Ref? = null): String


  /****************************************************************************/
  /**
  * Converts a single collection to string form.
  *
  * @param refCollection Item to be handled.
  * @param format Optional format string.
  * @param context Optional Ref giving context information.
  */

  abstract fun toString (refCollection: RefCollection, format: String? = null, context: Ref? = null): String
}