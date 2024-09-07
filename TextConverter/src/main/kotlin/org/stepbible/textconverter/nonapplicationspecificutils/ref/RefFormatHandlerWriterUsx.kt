package org.stepbible.textconverter.nonapplicationspecificutils.ref

/****************************************************************************/
/**
 * A writer for USX references.
 *
 * @author ARA "Jamie" Jamieson
 */

 object RefFormatHandlerWriterUsx : RefFormatHandlerWriterBase()
 {
  /****************************************************************************/
  /**
  * Converts a single reference to string form.
  *
  * @param ref Reference to be handled.
  * @param format Optional format string.
  * @param context Optional Ref giving context information.
  */

  override fun toString (ref: Ref, format: String?, context: Ref?): String
  {
    return ref.toStringUsx(format ?: "bcvs", context)
  }


  /****************************************************************************/
  /**
  * Converts a reference range to string form.
  *
  * @param refRange Item to be handled.
  * @param format Optional format string.
  * @param context Optional Ref giving context information.
  */

  override fun toString (refRange: RefRange, format: String?, context: Ref?): String
  {
    return refRange.toStringUsx(format ?: "bcvs", context)
  }


  /****************************************************************************/
  /**
  * Converts a single collection to string form.
  *
  * @param refCollection Item to be handled.
  * @param format Optional format string.
  * @param context Optional Ref giving context information.
  */

  override fun toString (refCollection: RefCollection, format: String?, context: Ref?): String
  {
    return refCollection.toStringUsx(format ?: "bcvs", context)
  }
 }