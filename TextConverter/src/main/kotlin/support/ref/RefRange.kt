/******************************************************************************/
package org.stepbible.textconverter.support.ref

import org.stepbible.textconverter.support.bibledetails.TextStructureEnhancedUsx
import org.stepbible.textconverter.support.debug.Logger


/******************************************************************************/
/**
 * Handles reference ranges.  Ranges come in a few different forms.  I think,
 * in fact, that I can probably *represent* all of them here; the main question
 * is whether you can then ask to have them expanded out into individual
 * references, should you wish to :-
 *
 * - Chapter-chapter: Yes, but the expanded result depends upon how much
 *   knowledge I have of the vernacular text at the time.  The expanded
 *   references run from v1 of the low chapter to the last verse of the high
 *   one.
 *
 * - Chapter-verse: Yes, but the expanded result depends upon how much
 *   knowledge I have of the vernacular text at the time.  The expanded
 *   references run from v1 of the low chapter to vN of the high one.
 *
 * - Verse-chapter: Yes, but the expanded result depends upon how much
 *   knowledge I have of the vernacular text at the time.  The expanded
 *   references run from vN of the low chapter to the last verse of the
 *   high one.
 *
 * - Verse-verse (different chapters): Yes, but the expanded result depends
 *   upon how much knowledge I have of the vernacular text at the time.  The
 *   expanded references run from vM of the low chapter to vN of the high one.
 *
 * - Verse-verse (same chapter): Yes.
 *
 * - Verse-subverse (same verse): Yes.  The expanded range runs from subverse 0
 *   to the given subverse.
 *
 * - Subverse-subverse (same verse): Yes.  The expanded range runs from subverse
 *   m to subverse N.
 *
 * - Anything else: No.  Any attempt to obtain the collection of subtended
 *   references will abort the processing.
 *
 * @author ARA "Jamie" Jamieson
 */

class RefRange: RefCollectionPart
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
   * Copy constructor.
   *
   * @param other Range to be copied.
   */

  constructor (other: RefRange)
  {
    m_Low = Ref(other.m_Low)
    m_High = Ref(other.m_High)
  }


  /****************************************************************************/
  /**
   * Construct from two references.  Note that the Refs are cloned, so any
   * change made externally to the Refs will not impact the range.
   *
   * @param low
   * @param high
   */

  constructor (low: Ref, high: Ref)
  {
    m_Low = Ref(low)
    m_High = Ref(high)
  }


  /******************************************************************************/
  /**
  * Formats the present instance using a particular format handler.
  *
  * @param formatHandler Format handler.
  * @param format Format string,
  * @param context Optional context used for defaulting.
  */

  override fun formatMe (formatHandler: RefFormatHandlerWriterBase, format: String, context: Ref?): String
  {
    return formatHandler.toString(this, format = format, context = context)
  }


  /******************************************************************************/
  /* OSIS references always have to be written out in full, so it's convenient to
     have a specific method for that. */

  override fun formatMeAsOsis (format: String): String
  {
    return if (isSingleReference()) getLowAsRef().formatMeAsOsis(format) else getLowAsRef().formatMeAsOsis(format) + "-" + getHighAsRef().formatMeAsOsis(format)
  }


  /******************************************************************************/
  /* USX references always have to be written out in full, so it's convenient to
     have a specific method for that. */

  override fun formatMeAsUsx (format: String, context: Ref?): String
  {
    return if (isSingleReference()) getLowAsRef().formatMeAsUsx(format, context) else getLowAsRef().formatMeAsUsx(format, context) + "-" + getHighAsRef().formatMeAsUsx(format, getLowAsRef())
  }


  /****************************************************************************/
  /**
   * Converts all contents to their refKeys and returns the whole lot.
   *
   * @return RefKeys.
   */

  override fun getAllAsRefKeys (): List<RefKey>
  {
     if (m_SavedRefKeys.isEmpty())
       m_SavedRefKeys = getAllAsRefKeysInternal()
     return m_SavedRefKeys
  }


  /****************************************************************************/
  /**
   * {@inheritDoc}
   */

   override fun getAllAsRefs (): List<Ref>
   {
     val  res: MutableList<Ref> = ArrayList()
     getAllAsRefKeys().forEach{ res.add(Ref.rd(it)) }
     return res
  }


  /****************************************************************************/
  /**
   * Counts the number of elements.  Note this is not the same as the number of
   * individual references -- a range counts as one element.
   *
   * @return Element count.
   */

  override fun getElementCount (): Int
  {
    return 2
  }


  /****************************************************************************/
  /**
   * Returns the first ref (which, if the first element is a range, will be the
   * first ref of that range).  Or returns null if the collection is empty.
   *
   * @return First ref or null.
   */

  override fun getFirstAsRef (): Ref
  {
    return m_Low
  }


  /****************************************************************************/
  /**
   * Returns the first ref (which, if the first element is a range, will be the
   * first ref of that range).  Or returns null if the collection is empty.
   *
   * @return First ref or null.
   */

  override fun getLowAsRef (): Ref
  {
    return getFirstAsRef()
  }


  /****************************************************************************/
  /**
   * Returns the last ref (which, if the first element is a range, will be the
   * last ref of that range).  Or returns null if the collection is empty.
   *
   * @return First ref or null.
   */

  override fun getHighAsRef (): Ref
  {
    return m_High
  }


  /****************************************************************************/
  /**
   * Returns the last ref (which, if the first element is a range, will be the
   * last ref of that range).  Or returns null if the collection is empty.
   *
   * @return First ref or null.
   */

  override fun getLastAsRef (): Ref
  {
    return getHighAsRef()
  }


  /****************************************************************************/
  /**
   * Gets the count of underlying references.  If the collection contains any
   * ranges, they count as n references.
   *
   * @return First ref or null.
   */

  override fun getReferenceCount (): Int
  {
    return getAllAsRefKeys().size
  }


  /****************************************************************************/
  /**
   * Particularly with USX, we may end up creating a 'range' which consists of
   * only a single reference.  This tells us if we have.
   *
   * @return True if this is a single-reference range.
   */

  fun isSingleReference (): Boolean
  {
    return getLowAsRefKey() == getHighAsRefKey()
  }


  /****************************************************************************/
  /**
  * Returns a single-element reference collection containing just this one
  * item.
  *
  * @return Reference collection.
  */

  fun toRefCollection (): RefCollection
  {
    return RefCollection(this)
  }





  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  companion object
  {
    /**************************************************************************/
    /* The various read methods.  We need to be able to read USX ranges, so
       there's a method for that (rd and rdUsx are the same, incidentally).
       And late in the day, we've decided we need to read OSIS too, in support
       of extended tagging, so there's a method for that.  But there is no
       method for reading vernacular material, because we have to assume that
       may appear embedded in a larger text string, and therefore cannot be
       returned as a simple range. */

    fun rd           (text: String, context: Ref? = null, resolveAmbiguitiesAs: String? = null): RefRange { return rdUsx         (text, context, resolveAmbiguitiesAs) }
    fun rdOsis       (text: String, context: Ref? = null, resolveAmbiguitiesAs: String? = null): RefRange { return rdOsisInternal(text, context, resolveAmbiguitiesAs) }
    fun rdUsx        (text: String, context: Ref? = null, resolveAmbiguitiesAs: String? = null): RefRange { return rdUsxInternal (text, context, resolveAmbiguitiesAs) }

    fun rd (refLow: Ref, refHigh: Ref): RefRange { return RefRange(refLow, refHigh) }


    /**************************************************************************/
    /* Because of the highly constrained circumstances under which we may
       encounter OSIS references, for much of the time I can guarantee that what
       we may be required to parse contains a fully populated reference or
       reference range.  This means that I can circumvent the generic parsing,
       and use something which is much more efficient -- useful given the number
       of times we may need parse OSIS references.

       Parsing OSIS is simpler than parsing USX, because (at least in theory)
       we are guaranteed to have full references (ie no defaulting from
       context).

       If you know in advance that you are not dealing with a situation where
       ranges might be an issue, you might want to use Ref.rdOsis instead.
     */

    private fun rdOsisInternal (text: String, context: Ref?, resolveAmbiguitiesAs: String?): RefRange
    {
      val bits = text.replace("\\s*-\\s*".toRegex(), "-").split("-")
      val refLow = Ref.rdOsis(bits[0], context, resolveAmbiguitiesAs)
      val refHigh = if (1 == bits.size) refLow else Ref.rdOsis(bits[1], context, resolveAmbiguitiesAs)
      return RefRange(refLow, refHigh)
    }


    /**************************************************************************/
    /* Because of the highly constrained circumstances under which we may
       encounter USX references, for much of the time I can guarantee that what
       we may be required to parse contains a fully populated reference or
       reference range.  This means that I can circumvent the generic parsing,
       and use something which is much more efficient -- useful given the number
       of times we may need parse USX references.

       This present method will parse either a single reference or a range.  If
       it is parsing only a single reference, it returns a range whose low and
       high references are the same.  You can use the isSingleReference method
       to check this.

       If you know in advance that you are not dealing with a situation where
       ranges might be an issue, you might want to use Ref.rdUsx instead.
     */

    private fun rdUsxInternal (text: String, context: Ref?, resolveAmbiguitiesAs: String?): RefRange
    {
      /************************************************************************/
      //Dbg.dCont(text,"A:1")



      /************************************************************************/
      val bits = text.replace("\\s+".toRegex(), " ").replace("\\s*-\\s*".toRegex(), "-").split("-")
      val refLow = Ref.rdUsx(bits[0], context, resolveAmbiguitiesAs)
      if (1 == bits.size) return RefRange(refLow, refLow)



      /************************************************************************/
      /* In theory, USX demands that all references be fully populated.  I'm
         fairly sure I've seen cases where the trailing reference contains, say,
         just a verse, and I may as well seek to accommodate this.  First,
         though, if the trailing reference contains a space (which is used as a
         book/chapter separator), I guess I can assume it's complete. */

      if (bits[1].contains(" "))
        return RefRange(refLow, Ref.rdUsx(bits[1]))



      /************************************************************************/
      /* The trailing reference doesn't contain a book.  If it contains a colon
         (which is the chapter/verse separator), then we know that at most it
         is lacking the book, which we can carry over from the leading
         reference. */

      if (bits[1].contains(":"))
        return RefRange(refLow, Ref.rdUsx(bits[1], refLow))



      /************************************************************************/
      /* The trailing reference contains neither book nor chapter.  If it isn't
         a pure subverse (ie letters only), then it's either a verse reference
         or a verse+subverse reference, and either way we can just prefix it
         with book and chapter. */

      if (!bits[1].matches("[a-z]+".toRegex()))
      {
        val prefix = bits[0].split(":")[0]
        return RefRange(refLow, Ref.rdUsx(prefix + ":" + bits[1], refLow))
      }



      /************************************************************************/
      /* Must be a subverse only (assuming we can get this in USX -- not sure
         we can, but I guess there's no harm in allowing for it. */

      val prefix = bits[0].replace("[a-z]+$", "")
      return RefRange(refLow, Ref.rdUsx(prefix + bits[1]))
    }
  } // companion object





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Slightly iffy ... Most ranges I can cope with properly.  The one which
     I can't cope with properly is a cross-verse range where subverses are
     involved, because here I have no way of knowing how many subverses there
     are in the lower verse.  In this case, I simply ignore the subverse
     designators.  Whether this is a reasonable thing to do -- and whether it
     is going to cause issues downstream -- I don't know. */

  private fun getAllAsRefKeysInternal (): List<RefKey>
  {
    val lowRefKey = m_Low.getFirstAsRefKey()
    val highRefKey = m_High.getLastAsRefKey()
    return getAllAsRefKeysInternal(lowRefKey, highRefKey) ?: getAllAsRefKeysInternal(Ref.clearS(lowRefKey), Ref.clearS(highRefKey))!!
  }


  /****************************************************************************/
  /* Note that throughout, BibleStructure.UsxUnderConstructionInstance() really
     _is_ meant -- the supplied text is the one we're working on.  We may have
     USX references, for instance, but they're always references which describe
     the supplied text. */

  private fun getAllAsRefKeysInternal (lowRefKey: RefKey, highRefKey: RefKey): List<RefKey>?
  {
    /**************************************************************************/
    if (lowRefKey == highRefKey)
    {
      val res: MutableList<RefKey> = ArrayList()
      res.add(lowRefKey)
      return res
    }


    /**************************************************************************/
    /* Chapter to chapter: take this as v1 of the low ref to the last verse of
       the high ref.  Note that there is no guarantee this will be correct,
       since I may not know for sure how many verses the chapters contain. */

    if (!Ref.hasV(lowRefKey) && !Ref.hasV(highRefKey))
    {
      var refKey: RefKey = lowRefKey
      val res: MutableList<RefKey> = ArrayList(1000)
      for (c in Ref.getC(lowRefKey)..Ref.getC(highRefKey))
      {
        refKey = Ref.setC(refKey, c)
        for (v in 1 .. TextStructureEnhancedUsx.getBibleStructure().getLastVerseNo(refKey))
          res.add(Ref.setV(refKey, v))
      }

      return res
    }



    /**************************************************************************/
    /* Chapter to verse: take this as v1 of the low ref to the given verse of
       the high ref.  Note that there is no guarantee this will be correct,
       since I may not know for sure how many verses the chapters contain.
       Note also that I silently ignore any subverse on the high ref. */

    if (!Ref.hasV(lowRefKey) && Ref.hasV(highRefKey))
    {
      var refKey: RefKey = lowRefKey
      val res: MutableList<RefKey> = ArrayList(1000)
      for (c in Ref.getC(lowRefKey)..< Ref.getC(highRefKey))
      {
        refKey = Ref.setC(refKey,c)
        for (v in 1 .. TextStructureEnhancedUsx.getBibleStructure().getLastVerseNo(refKey))
          res.add(Ref.setV(refKey, v))
      }

      refKey = highRefKey
      for (v in 1 .. Ref.getV(highRefKey))
        res.add(Ref.setV(refKey, v))

      return res
    }



    /**************************************************************************/
    /* Verse to chapter: take this as vN of the low ref to the last verse of
       the high ref.  Note that there is no guarantee this will be correct,
       since I may not know for sure how many verses the chapters contain.
       Note also that I silently ignore any subverse indicators. */

    if (Ref.hasV(lowRefKey) && !Ref.hasV(highRefKey))
    {
      var refKey: RefKey = Ref.clearS(lowRefKey)
      val res: MutableList<RefKey> = ArrayList(1000)

      for (v in 1 .. TextStructureEnhancedUsx.getBibleStructure().getLastVerseNo(refKey))
        res.add(Ref.setV(refKey, v))

      for (c in Ref.getC(lowRefKey) + 1 .. Ref.getC(highRefKey))
      {
        refKey = Ref.setC(refKey,c)
        for (v in 1 .. TextStructureEnhancedUsx.getBibleStructure().getLastVerseNo(refKey))
          res.add(Ref.setV(refKey, v))
      }

      return res
    }



    /*************************************************************************/
    /* Verse to verse of same chapter or different chapters. */

    if (!Ref.hasS(lowRefKey) && !Ref.hasS(highRefKey))
      return TextStructureEnhancedUsx.getBibleStructure().getRefKeysInRange(lowRefKey, highRefKey)



    /**************************************************************************/
    /* Verse to subverse of same verse.  We take this as covering subverse
       zero through to the given subverse. */

    if (!Ref.hasS(lowRefKey) && Ref.hasS(highRefKey) && m_Low.toRefKey_bcv() == m_High.toRefKey_bcv())
    {
      val res: MutableList<RefKey> = ArrayList()
      for (s in 0 .. Ref.getS(highRefKey))
        res.add(Ref.setS(lowRefKey, s))

      return res
    }



    /**************************************************************************/
    /* Subverse to subverse (both of which must be in the same verse). */

    if (Ref.hasS(lowRefKey) && Ref.hasS(highRefKey) && m_Low.toRefKey_bcv() == m_High.toRefKey_bcv())
    {
      val res: MutableList<RefKey> = ArrayList()
      for (s in Ref.getS(lowRefKey) .. Ref.getS(highRefKey))
        res.add(Ref.setS(lowRefKey, s))

      return res
    }



    /**************************************************************************/
    /* That leaves cross-verse ranges with a subverse at one or other end, etc.
       Anything like this we can't cope with. */

    Logger.warning("Cross-verse subverse range (may not expand correctly): " + toString())
    return null
  }


  /****************************************************************************/
  private var m_Low: Ref
  private var m_High: Ref
  private var m_SavedRefKeys: List<RefKey> = ArrayList()
}