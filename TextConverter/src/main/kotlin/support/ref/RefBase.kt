/****************************************************************************/
package org.stepbible.textconverter.support.ref

import org.stepbible.textconverter.support.debug.Dbg
import kotlin.math.abs


/****************************************************************************/
/**
 * Base class for references.
 *
 * The present class defines certain things which are reasonably obviously
 * common across all of these flavours of reference, plus some things which
 * are not.  It is, for instance, reasonable that we might want to find the
 * first reference which forms part of a reference range; it is less obvious
 * that you would want to find the first reference associated with a Ref,
 * since that *has* only one element.  However, there is no harm in having
 * this available, and defining it here does at least mean that we have a
 * consistent API across the board.
 *
 * @author ARA "Jamie" Jamieson
 */

abstract class RefBase
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
   * An individual reference is made up of four elements: a book, a chapter, a
   * verse and optionally a subverse.  When a reference instance is created by
   * reading it from a string, each of these may be obtained directly from the
   * data supplied, or from any contextual default, or may be undefined.
   */

  enum class ElementSource { Explicit, FromContext, ImplicitBecauseSingleChapterBook, Undefined }


  /******************************************************************************/
  /**
   * Obtains a list of all the references represented by an item.  For Ref,
   * the list has a single element -- the Ref itself.  For a RefRange, it
   * contains all of the individual references implied by the range.  For a
   * collection, it comprises the expanded forms of each of the elements.
   *
   * @return List of Refs.
   */

  abstract fun getAllAsRefs (): List<Ref>


  /******************************************************************************/
  /**
   * Obtains a list of all the refkeys represented by an item.  For Ref,
   * the list has a single element -- the Ref itself.  For a RefRange, it
   * contains all of the individual refKeys implied by the range.  For a
   * collection, it comprises the refKeys for the expanded forms of each of the
   * elements.
   *
   * @return List of refkeys.
   */

  abstract fun  getAllAsRefKeys (): List<RefKey>


  /******************************************************************************/
  /**
   * Returns the count of elements making up the reference or collection.
   * For Ref, this will be 1.  For RefRange, it will be 2.  For RefCollection,
   * for example one of the form x, y, z, it will be 3, even if x, y or z is
   * itself a range which covers many separate verses.
   *
   * @return Count.
   */

  abstract fun getElementCount (): Int

  
  /******************************************************************************/
  /**
   * Obtains the first reference and returns it as a Ref.  For Refs, this is
   * the reference itself.  For RefRanges, it is the low reference.  For
   * RefCollections, it is the first Ref (if the RefCollection begins with a
   * Ref), or the first Ref of the first RefRange (if it starts with RefRange).
   *
   * @return First reference as Ref.
   */

  abstract fun getFirstAsRef (): Ref

  
  /******************************************************************************/
  /**
   * Obtains the first reference and returns it as a refkey.  For Refs, this is
   * the refKey itself.  For RefRanges, it is the low refKey.  For
   * RefCollections, it is the first refKey (if the RefCollection begins with a
   * Ref), or the first refKey of the first RefRange (if it starts with
   * RefRange).
   *
   * @return First reference as refKey.
   */

  fun getFirstAsRefKey (): RefKey { return getFirstAsRef().toRefKey() }

  
  /******************************************************************************/
  /**
   * Obtains the last reference and returns it as a Ref.  For Refs, this is
   * the reference itself.  For RefRanges, it is the high reference.  For
   * RefCollections, high ref of the last element.
   *
   * @return First reference as Ref.
   */

  abstract fun getLastAsRef (): Ref

  
  /******************************************************************************/
    /**
     * Obtains the last reference and returns it as a refkey.  For Refs, this is
     * the refKey itself.  For RefRanges, it is the high refKey.  For
     * RefCollections, it is the last refKey of the last element).
     *
     * @return First reference as refKey.
     */

    fun getLastAsRefKey (): RefKey { return getLastAsRef().toRefKey() }

  
  /******************************************************************************/
  /**
   * Obtains the low reference and returns it as a Ref.  For Refs, this is
   * the reference itself.  For RefRanges, it is the low reference.  For
   * RefCollections, it is the lowest reference of any in the collection.
   *
   * @return Low reference as Ref.
   */

  abstract fun getLowAsRef (): Ref

  
  /******************************************************************************/
    /**
     * Obtains the low reference and returns it as a refkey.  For Refs, this is
     * the reference itself.  For RefRanges, it is the low reference.  For
     * RefCollections, it is the lowest reference of any in the collection.
     *
     * @return Low reference as refkey.
     */

    fun getLowAsRefKey (): RefKey { return getLowAsRef().toRefKey() }

  
  /******************************************************************************/
  /**
   * Obtains the high reference and returns it as a Ref.  For Refs, this is
   * the reference itself.  For RefRanges, it is the high reference.  For
   * RefCollections, it is the highest reference of any in the collection.
   *
   * @return High reference as Ref.
   */

  abstract fun getHighAsRef (): Ref

  
  /******************************************************************************/
  /**
   * Obtains the high reference and returns it as a refkey.  For Refs, this is
   * the reference itself.  For RefRanges, it is the high reference.  For
   * RefCollections, it is the highest reference of any in the collection.
   *
   * @return High reference as refkey.
   */

  fun getHighAsRefKey (): RefKey { return getHighAsRef().toRefKey() }

  
  /******************************************************************************/
  /**
   * Returns the count of references making up the reference or collection.
   * For Ref, this is 1.  For RefRange, it is number of verses or subverses from
   * start to end, inclusive.  For a RefCollection consisting, say, of m ranges
   * and n individual verses, it is the sum of the number of individual references
   * covered by each of the various ranges, plus n.
   *
   * @return Count.
   */

  abstract fun getReferenceCount (): Int


  /******************************************************************************/
  /* You will see below a whole collection of functions related to writing
     references.  The advantage of having them here is that various of the
     methods are related in that one calls another, and I can record these
     relationships here, rather than have to do it in the derived classes.

     I'd _like_ to be able to do the same for reading references too.  However,
     the various reading functions differ not in terms of their arguments, but
     in terms of their return types, and polymorphism won't work there. */


  /******************************************************************************/
  /* toString and toStringUsx are the same thing.

     You can call the toString functions with no arguments, or with either or
     both of a format string and a reference which gives contextual information,
     thus allowing portions of the returned reference to be defaulted.  (If,
     for example, the reference to be written out is Jn 3:16 and the context is
     Jn 2, the 'Jn' portion of the result may be suppressed, depending upon the
     context string. */

  override fun toString (): String { return toString(format = "bcvs") }
  fun toString (context:Ref?): String { return toString(format="bcvs", context = context) }
  fun toString (format: String = "bcvs", context: Ref? = null): String  { return toStringUsx(format = format, context = context) }

  fun toStringOsis (): String { return toStringOsis(format = "bcvs") }
  fun toStringOsis (context:Ref?): String { return toStringOsis(format="bcvs", context = context) }
  fun toStringOsis (format: String = "bcvs", context: Ref? = null): String   { return formatMeAsOsis(format) }

  fun toStringUsx (): String { return toStringUsx(format = "bcvs") }
  fun toStringUsx (context:Ref?): String { return toStringUsx(format="bcvs", context = context) }
  fun toStringUsx (format: String = "bcvs", context: Ref? = null): String { return formatMeAsUsx(format, context) }

  fun toStringVernacular (): String { return toStringVernacular(format = "bcvs") }
  fun toStringVernacular (context:Ref?): String { return toStringVernacular(format="bcvs", context = context) }
  fun toStringVernacular (format: String = "bcvs", context: Ref? = null): String { return formatMe(RefFormatHandlerWriterVernacular, format = format, context = context) }

  abstract fun formatMe (formatHandler: RefFormatHandlerWriterBase, format:String = "bcvs", context: Ref? = null): String
  abstract fun formatMeAsOsis (format: String): String
  abstract fun formatMeAsUsx (format: String, context:Ref?): String






  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  companion object
  {
    /****************************************************************************/
    /****************************************************************************/
    /**                                                                         */
    /**                               Public                                    */
    /**                                                                         */
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
     * In the Vulgate, some chapters are numbered A, B, etc.  I need a way of
     * fitting them into the overall scheme of things (which is set up only to
     * handle numerical values).  To that end, chapter A becomes 601, B 602, etc.
     */

    const val C_AlphaChapterOffset: Int = 600
  
  
    /******************************************************************************/
    /* During processing it is convenient to introduce a dummy verse at the end of
       each chapter with an artificially high number, so that any verses which need
       to be inserted can definitely be inserted before *something*, even if they
       actually come at the end of the chapter. */

    const val C_BackstopVerseNumber = 500


    /******************************************************************************/
    /**
     * The value used in respect of a single element to indicate that a genuine
     * value has not been provided for it.
     */

    const val C_DummyElement: Int = 0
  
  
    /******************************************************************************/
    /**
     * The multiplier used to combine the individual elements into a single RefKey
     * for use as a refKey.
     */

    const val C_Multiplier: RefKey = 1000
  
  
    /****************************************************************************/
    /**
     * Used only with reversification data, which sometimes refers eg to
     * Psa 51:Title, and I need to turn it into a pretend verse.
     */

    const val C_TitlePseudoVerseNumber = 499


    /******************************************************************************/
    /**
     * Returns an indication of whether two refKeys correspond to consecutive
     * verses.  Subverses are regarded as being part of the verse, so a verse
     * and a subverse of that verse are regarded as consecutive; and two subverses
     * of the same verse are regarded as consecutive.  Items are regarded as
     * consecutive only if they are in the same chapter.  If both refKeys are the
     * same, they are not regarded as consecutive.
     *
     * @param refKeyA One refkey.
     * @param refKeyB One refkey.
     * @return True if consecutive.
     */

    fun consecutiveVerses (refKeyA: RefKey, refKeyB: RefKey): Boolean
    {
      if (refKeyA == refKeyB) return false
      return abs( (refKeyA / C_Multiplier) - (refKeyB / C_Multiplier) ) <= 1
    }


    /******************************************************************************/
    /**
     * Indicates whether a given value is the dummy value.
     *
     * @param x Value to be tested.
     *
     * @return True if this is the dummy value.
     */

    fun isDummyValue (x: Int): Boolean
    {
      return C_DummyElement == x
    }
  }
}


/**********************************************************************************/
typealias RefKey = Long