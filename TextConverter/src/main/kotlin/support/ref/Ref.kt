/****************************************************************************/
package org.stepbible.textconverter.support.ref

import org.stepbible.textconverter.support.bibledetails.BibleAnatomy
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.convertRepeatingStringToNumber
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.convertNumberToRepeatingString
import org.stepbible.textconverter.support.stepexception.StepException


/****************************************************************************/
/**
 * An individual reference (ie to a single book, a single chapter, a single
 * verse or a single subverse).  Stand by for a lot of explanation ...
 *
 * Quite a number of methods below contain in their names, or take as arguments,
 * some combination of letters from b(ook), c(hapter), v(erse) or s(ubverse).
 * Internally, each of these elements is held as a separate number in a four-
 * element array (the UBS book number in the case of the book element).  A
 * value of zero in any of them indicates that the corresponding element is
 * absent.  (In fact, there is a complication as regards subverse zero in the
 * context of reversification, but I'll discuss that there.)  The value of
 * zero is represented as C_DummyElement.
 *
 * These four elements are held as a 4-element array, ordered b-c-v-s.
 * A second array holds details of where the element contents were obtained
 * from.  A value of Undefined here indicates that the element does not have a
 * valid value.  A value of Explicit implies that something has explicitly
 * supplied the value (this is reliable really only when the Ref was obtained
 * by parsing text data).  A value of FromContext (again, really meaningful
 * only when parsing input strings for reference data) indicates that the value
 * was obtained from context (for example, a string containing '3:16' may well
 * give rise to a valid reference, but only if the book can be deduced from
 * context; in this case, the book entry will hold the value for John, but
 * we will also record the fact that it has been deduced).
 *
 * There is also an alternative format in which the four elements are combined
 * into a single RefKey known as a RefKey -- something which may usefully serve as
 * a key in data structures, and has the added advantage that a set of refKeys
 * ordered numerically corresponds to scripture ordering.
 *
 * Refkeys are designed to be reasonably human-readable for debug purposes
 * the right-hand three digits give the subverse number, the next three
 * the verse, and so on.
 *
 * There are a large number of setters and accessors which let you deal directly
 * with these various forms -- obtaining them, building Refs from them, or
 * tweaking parts of them.
 *
 * Note that even though there are potentially a very large number of different
 * schemes / formats for representing scripture references (USX, OSIS, and the
 * various vernaculars to name just a few), these are all artefacts of the
 * external world, where they are held in string format.  The present class is
 * not aware of these differences.
 *
 * @author ARA "Jamie" Jamieson
 */

class Ref : RefCollectionPart
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /** Constructors. */

  constructor () { for (i in m_RefElts.indices) { m_RefElts[i] = C_DummyElement; m_RefEltSources[i] = ElementSource.Undefined } }
  constructor (ref: Ref) { copyElements(ref.getElements()); copyElementSources(ref.getElementSources()) }


  /******************************************************************************/
  /** Copy fields from another instance, return copies.  The getAll methods
   *  won't be useful with this class, but are retained so that it presents the
   *  same API as other things. */

  fun copyElements (elts: IntArray) { m_RefElts = elts.clone() }
  fun copyElementSources (eltSources: Array<ElementSource>) { m_RefEltSources = eltSources.clone() }

  fun getCopyOfElements (): IntArray { return m_RefElts.clone() }
  fun getCopyOfElementSources (): Array<ElementSource> { return m_RefEltSources.copyOf() }

  override fun getAllAsRefs(): List<Ref> { return listOf(this) }
  override fun getAllAsRefKeys(): List<RefKey> { return listOf(getFirstAsRefKey()) }

  private fun getElements (): IntArray { return m_RefElts }
  private fun getElementSources (): Array<ElementSource> { return m_RefEltSources }


  /****************************************************************************/
  /** Comparisons.  cf returns -1, +1 or 0, and really assumes that none of the
   *  elements is empty. */

  fun cf (other: Ref): Int { return toRefKey().compareTo(other.toRefKey()) }
  override fun equals (other: Any?): Boolean { return null != other && other is Ref && m_RefElts.contentEquals(other.m_RefElts) }
  override fun hashCode (): Int { return 0 }


  /****************************************************************************/
  /** Gets, sets, clears, and checks for the existence of a given element.  */

  fun getB () = m_RefElts[C_BookIx]
  fun getC () = m_RefElts[C_ChapterIx]
  fun getV () = m_RefElts[C_VerseIx]
  fun getS () = m_RefElts[C_SubverseIx]
  fun get (ix: Int) = m_RefElts[ix]
  fun get (elt: Char  ) = get(getIndex(elt))
  fun get (elt: String) = get(getIndex(elt))

  fun setB (value: Int) { set(C_BookIx,     value) }
  fun setC (value: Int) { set(C_ChapterIx,  value) }
  fun setV (value: Int) { set(C_VerseIx,    value) }
  fun setS (value: Int) { set(C_SubverseIx, value) }
  fun set (ix: Int, value: Int) { m_RefElts[ix] = value; }
  fun set (elt: Char,   value: Int) { set(getIndex(elt), value); }
  fun set (elt: String, value: Int) { set(getIndex(elt), value); }

  fun clearB() { clear(C_BookIx) }
  fun clearC() { clear(C_ChapterIx) }
  fun clearV() { clear(C_VerseIx) }
  fun clearS() { clear(C_SubverseIx) }
  fun clear (ix: Int) { m_RefElts[ix] = C_DummyElement; m_RefEltSources[ix]  = ElementSource.Undefined }
  fun clear (elt: Char  ) { clear(getIndex(elt)) }
  fun clear (elt: String) { clear(getIndex(elt)) }

  fun hasB (): Boolean { return C_DummyElement != m_RefElts[C_BookIx] }
  fun hasC (): Boolean { return C_DummyElement != m_RefElts[C_ChapterIx] }
  fun hasV (): Boolean { return C_DummyElement != m_RefElts[C_VerseIx] }
  fun hasS (): Boolean { return C_DummyElement != m_RefElts[C_SubverseIx] }
  fun has (ix: Int): Boolean { return C_DummyElement != m_RefElts[ix] }
  fun has (elt: Char  ): Boolean { return  has(m_RefElts[getIndex(elt)]) }
  fun has (elt: String): Boolean { return  has(getIndex(elt)) }



  /****************************************************************************/
  /** Gets, sets and tests the element source of a given element.  */

  fun getSourceB (): ElementSource { return getSource(C_BookIx) }
  fun getSourceC (): ElementSource { return getSource(C_ChapterIx) }
  fun getSourceV (): ElementSource { return getSource(C_VerseIx) }
  fun getSourceS (): ElementSource { return getSource(C_SubverseIx) }
  fun getSource (ix: Int): ElementSource { return m_RefEltSources[ix] }
  fun getSource (elt: Char  ): ElementSource { return getSource(getIndex(elt)) }
  fun getSource (elt: String): ElementSource { return getSource(getIndex(elt)) }

  fun setSourceB (source: ElementSource) { setSource(C_BookIx,     source) }
  fun setSourceC (source: ElementSource) { setSource(C_ChapterIx,  source) }
  fun setSourceV (source: ElementSource) { setSource(C_VerseIx,    source) }
  fun setSourceS (source: ElementSource) { setSource(C_SubverseIx, source) }
  fun setSource (ix: Int, source: ElementSource) { m_RefEltSources[ix]  = source }
  fun setSource (elt: Char,   source: ElementSource) {setSource(getIndex(elt), source) }
  fun setSource (elt: String, source: ElementSource) {setSource(getIndex(elt), source) }

  fun hasExplicitB (): Boolean { return hasExplicit(C_BookIx) }
  fun hasExplicitC (): Boolean { return hasExplicit(C_ChapterIx) }
  fun hasExplicitV (): Boolean { return hasExplicit(C_VerseIx) }
  fun hasExplicitS (): Boolean { return hasExplicit(C_SubverseIx) }
  fun hasExplicit (ix: Int): Boolean { return ElementSource.Explicit == m_RefEltSources[ix] }
  fun hasExplicit (elt: Char  ): Boolean { return hasExplicit(getIndex(elt)) }
  fun hasExplicit (elt: String): Boolean { return hasExplicit(getIndex(elt)) }


  /****************************************************************************/
  /** Convert to refKey form. */

  fun toRefKey (): RefKey { return refKeyFromElts(m_RefElts) }
  fun toRefKey (ref: Ref, selectors: String): RefKey { return refKeyFromElts(ref.m_RefElts, selectors) }
  fun toRefKey_bcvs (): RefKey { return toRefKey() }
  fun toRefKey_bcv  (): RefKey { return toRefKey("bcv") }
  fun toRefKey_bc   (): RefKey { return toRefKey("bc") }
  fun toRefKey_vs   (): RefKey { return toRefKey("vs") }
  fun toRefKey (selectors: String): RefKey { return refKeyFromElts(m_RefElts, selectors) }


  /****************************************************************************/
  override fun getFirstAsRef (): Ref { return this }
  override fun getHighAsRef () : Ref { return this }
  override fun getLastAsRef () : Ref { return this }
  override fun getLowAsRef ()  : Ref { return this }

  
  /****************************************************************************/
  override fun getElementCount ()  : Int { return 1 }
  override fun getReferenceCount (): Int { return 1 }


  /****************************************************************************/
  fun isSameChapter (otherRef: Ref): Boolean { return toRefKey_bc() == otherRef.toRefKey_bc() }



  /****************************************************************************/
  /**
  * This returns a modified reference, based upon the present one.  It is called
  * when you have a reference which you know is ambiguous -- for example, you
  * may have read something which was interpreted as a 'c', but which you know
  * could also be interpreted as a 'v', and you want a revised reference where
  * this change has been made.
  *
  * Note that error-checking is non-existent, so buyer beware.
  *
  * @param want: A bcvs string indicating which elements you want in the output.
  * @param have: A bcvs string indicating which elements are to be used as input.
  * @return Revised reference.
  */

  fun convertRefToAlternative (want: String, have: String): Ref
  {
    val newElts = intArrayOf(RefBase.C_DummyElement, RefBase.C_DummyElement, RefBase.C_DummyElement, RefBase.C_DummyElement)
    val newSources = arrayOf(RefBase.ElementSource.Undefined, RefBase.ElementSource.Undefined, RefBase.ElementSource.Undefined, RefBase.ElementSource.Undefined)

    for (i in want.indices)
    {
      val existingValue = m_RefElts[getIndex(have[i])]
      val ix = getIndex(want[i])
      newElts[ix] = existingValue
      newSources[ix] = ElementSource.Explicit
    }

    return Ref.rd(newElts, newSources)
  }


  /******************************************************************************/
  /* Used for things other than USX and OSIS, which are tightly constrained and
     therefore more cheaply handled by their own specific methods. */

  override fun formatMe (formatHandler: RefFormatHandlerWriterBase, format: String, context: Ref?): String
  {
    return formatHandler.toString(this, format = format, context = context)
  }


  /******************************************************************************/
  /* OSIS references always have to be written out in full, so it's convenient to
     have a specific method for that. */

  override fun formatMeAsOsis (format: String): String
  {
    if ("b" == format)
      return BibleBookNamesOsis.numberToAbbreviatedName(getB())
    else if ("bc" == format)
      return BibleBookNamesOsis.numberToAbbreviatedName(getB()) + "." + getC().toString()
    else
    {
      var res = BibleBookNamesOsis.numberToAbbreviatedName(getB()) + "." + getC().toString() + "." + getV().toString()
      if (hasS()) res += "!" + convertNumberToRepeatingString(getS(), 'a', 'z')
      return res
    }
  }


  /******************************************************************************/
  /* I don't _think_ we will ever want to default USX refs from context because
     of the way they are used, but no harm in allowing for it.  As regards
     the format string, normally we write them out in full (or as full as
     permitted by the elements which have been provided), but again I permit
     other possibilities (which in this case are actually used -- there are
     some cases where I need to be able to come up with the USX form of a
     chapter reference given a verse reference). */

  override fun formatMeAsUsx (format: String, context: Ref?): String
  {
    val myFormat = format.lowercase()
    val formatAsIs = myFormat.contains("a")
    val augmentedRef = Ref.rd(this, if (formatAsIs) null else context)
    var res = ""

    var wantIt = augmentedRef.hasB() && myFormat.contains("b"); wantIt = wantIt || (formatAsIs && augmentedRef.hasExplicitB())
    if (wantIt) res = BibleBookNamesUsx.numberToAbbreviatedName(augmentedRef.getB())

    wantIt = augmentedRef.hasC() && myFormat.contains("c"); wantIt = wantIt || (formatAsIs && augmentedRef.hasExplicitC())
    if (wantIt) res += " " + converterChapterNumberToString(augmentedRef.getC())

    wantIt = augmentedRef.hasV() && myFormat.contains("v"); wantIt = wantIt || (formatAsIs && augmentedRef.hasExplicitV())
    if (wantIt) res += ":" + augmentedRef.getV().toString()

    wantIt = augmentedRef.hasS() && myFormat.contains("s"); wantIt = wantIt || (formatAsIs && augmentedRef.hasExplicitS())
    if (wantIt) res += convertNumberToRepeatingString(augmentedRef.getS(), 'a', 'z')

    res = res.trim() // Gets rid of the leading space if we don't have a book.
    if (res.startsWith(":"))  res = res.substring(1) // Ditto the chapter / verse separator.

    return res
  }


  /******************************************************************************/
  private fun converterChapterNumberToString (chapterNo: Int): String
  {
    if (chapterNo < RefBase.C_AlphaChapterOffset) return chapterNo.toString()
    return convertNumberToRepeatingString(chapterNo - RefBase.C_AlphaChapterOffset, 'A', 'Z')
  }


  /******************************************************************************/
  /**
   * Returns bcvs or some subset to indicate which elements of the reference
   * were supplied explicitly.
   *
   * @return bcvs or some subset.
   */

  fun getExplicitElementSelectors (): String
  {
    var res = ""
    if (m_RefEltSources[0] == ElementSource.Explicit) res += "B"
    if (m_RefEltSources[1] == ElementSource.Explicit) res += "C"
    if (m_RefEltSources[2] == ElementSource.Explicit) res += "V"
    if (m_RefEltSources[3] == ElementSource.Explicit) res += "S"
    return res
  }

  
  /******************************************************************************/
  /**
  * Returns b / c/ v/ s according to the lowest level defined within this ref.
  *
  * @return b / c / v s.
  */

  fun getLowestLevelDefinedAsChar (): Char
  {
    return getKeyAsChar(m_RefElts.indexOfLast { has(it) })
  }


  /******************************************************************************/
  /**
  * Returns b / c/ v / s according to the lowest level defined within this ref.
  *
  * @return b / c / v / s.
  */

  fun getLowestLevelDefinedAsString (): String
  {
    return getKeyAsString(m_RefElts.indexOfLast { C_DummyElement != it })
  }


  /******************************************************************************/
  /**
  * Checks if there are any holes in the elements of the reference.
  *
  * @return True if there are holes.
  */

  fun hasHoles (): Boolean
  {
    val ixLow  = m_RefElts.indexOfFirst { it != C_DummyElement }
    val ixHigh = m_RefElts.indexOfLast  { it != C_DummyElement }
    for (i in ixLow + 1 until ixHigh)
      if (C_DummyElement == m_RefElts[i]) return true
    return false
  }


  /******************************************************************************/
  /**
  * Merges data into the present reference from another one which acts like
  * a context.  Leading elements are filled in if possible.
  *
  * @param context Other reference.
  */

  fun mergeFromOther (context: Ref?)
  {
    if (null == context) return

    for (i in m_RefElts.indices)
      if (C_DummyElement == m_RefElts[i])
      {
        m_RefElts[i] = context.m_RefElts[i]
        m_RefEltSources[i] = if (C_DummyElement == m_RefElts[i]) ElementSource.Undefined else ElementSource.FromContext
      }
      else
        break
  }


  /******************************************************************************/
  /**
   * Sets a given element in the ElementSources array.  This lets you pretend,
   * for instance, that something which was filled in explicitly had actually
   * been supplied from context, thus suppressing its output in formatted
   * strings.
   *
   * @param ix Index into array.
   * @param eltSource New value.
   */

  fun setElementSource (ix: Int, eltSource: ElementSource)
  {
    m_RefEltSources[ix] = eltSource
  }


  /******************************************************************************/
  /**
   * Sets a given element in the ElementSources array.  This lets you pretend,
   * for instance, that something which was filled in explicitly had actually
   * been supplied from context, thus suppressing its output in formatted
   * strings.
   *
   * @param category Implied index into array.
   * @param eltSource New value.
   */

  fun setElementSource (category: Char, eltSource: ElementSource)
  {
    setElementSource(getIndex(category), eltSource)
  }





  /******************************************************************************/
  /******************************************************************************/
  /**                                                                           */
  /**                                  Private                                  */
  /**                                                                           */
  /******************************************************************************/
  /******************************************************************************/

  
  /******************************************************************************/
  private fun toElts (theRefKey: RefKey)
  {
    var refKey = theRefKey
                            m_RefElts[C_SubverseIx] = (refKey % C_Multiplier).toInt()
    refKey /= C_Multiplier; m_RefElts[C_VerseIx   ] = (refKey % C_Multiplier).toInt()
    refKey /= C_Multiplier; m_RefElts[C_ChapterIx ] = (refKey % C_Multiplier).toInt()
    refKey /= C_Multiplier; m_RefElts[C_BookIx    ] = refKey.toInt()
    for (i in m_RefElts.indices) m_RefEltSources[i] = if (C_DummyElement == m_RefElts[i]) ElementSource.Undefined else ElementSource.Explicit
  }

  
  /******************************************************************************/
  private var m_RefElts = IntArray(4)
  private var m_RefEltSources = arrayOf(ElementSource.Undefined, ElementSource.Undefined, ElementSource.Undefined, ElementSource.Undefined)





  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  companion object
  {
    fun getB (refKey: RefKey): Int { return (refKey / C_BookFactor).toInt() }
    fun getC (refKey: RefKey): Int { return (refKey / C_ChapterFactor % C_Multiplier).toInt() }
    fun getV (refKey: RefKey): Int { return (refKey / C_VerseFactor % C_Multiplier).toInt() }
    fun getS (refKey: RefKey): Int { return (refKey / C_SubverseFactor % C_Multiplier) .toInt()}

    fun setB (refKey: RefKey, x: Int): RefKey { return x            * C_BookFactor + getC(refKey) * C_ChapterFactor + getV(refKey) * C_Multiplier + getS(refKey) }
    fun setC (refKey: RefKey, x: Int): RefKey { return getB(refKey) * C_BookFactor + x            * C_ChapterFactor + getV(refKey) * C_Multiplier + getS(refKey) }
    fun setV (refKey: RefKey, x: Int): RefKey { return getB(refKey) * C_BookFactor + getC(refKey) * C_ChapterFactor + x            * C_Multiplier + getS(refKey) }
    fun setS (refKey: RefKey, x: Int): RefKey { return getB(refKey) * C_BookFactor + getC(refKey) * C_ChapterFactor + getV(refKey) * C_Multiplier + x            }

    fun clearB (refKey: RefKey): RefKey { val r = rd(refKey); r.clearB(); return refKeyFromElts(r.m_RefElts) }
    fun clearC (refKey: RefKey): RefKey { val r = rd(refKey); r.clearC(); return refKeyFromElts(r.m_RefElts) }
    fun clearV (refKey: RefKey): RefKey { val r = rd(refKey); r.clearV(); return refKeyFromElts(r.m_RefElts) }
    fun clearS (refKey: RefKey): RefKey { val r = rd(refKey); r.clearS(); return refKeyFromElts(r.m_RefElts) }

    fun hasB (refKey: RefKey): Boolean { return C_DummyElement != getB(refKey) }
    fun hasC (refKey: RefKey): Boolean { return C_DummyElement != getC(refKey) }
    fun hasV (refKey: RefKey): Boolean { return C_DummyElement != getV(refKey) }
    fun hasS (refKey: RefKey): Boolean { return C_DummyElement != getS(refKey) }
  
  
    /****************************************************************************/
    /* Creates references from various forms of textual input. */

    fun rd           (text: String, context: Ref? = null, resolveAmbiguitiesAs: String? = null): Ref { return rdUsx(text, context, resolveAmbiguitiesAs)  }
    fun rdOsis       (text: String, context: Ref? = null, resolveAmbiguitiesAs: String? = null): Ref { return rdOsisInternal(text, context, resolveAmbiguitiesAs)  }
    fun rdUsx        (text: String, context: Ref? = null, resolveAmbiguitiesAs: String? = null): Ref { return rdUsxInternal(text, context, resolveAmbiguitiesAs)  }
    fun rdVernacular (text: String, context: Ref? = null, resolveAmbiguitiesAs: String? = null): Ref { return rdCommon(RefFormatHandlerReaderVernacular, text, context, resolveAmbiguitiesAs)  }


  
    /****************************************************************************/
    /** Read from other Refs or from elements. */
  
    fun rd (ref: Ref): Ref { return rd(ref.m_RefElts, ref.m_RefEltSources) }
    fun rd (ref: Ref, context: Ref?): Ref { val res = Ref(ref); res.mergeFromOther(context); return res }
    fun rd (refKey: RefKey, context: Ref? = null): Ref { val res = Ref(); res.toElts(refKey); res.mergeFromOther(context); return res }
    fun rd (elts: IntArray): Ref { return rd(elts[C_BookIx], elts[C_ChapterIx], elts[C_VerseIx], elts[C_SubverseIx]) }
    fun rd (elts: IntArray, elementSources: Array<ElementSource>): Ref { val res = rd(elts); res.m_RefEltSources = elementSources.clone(); return res; }
    fun rd (b: Int, c: Int, v: Int, s: Int = C_DummyElement): Ref { val res = Ref(); res.setB(b); res.setC(c); res.setV(v); res.setS(s); return res; }



    /****************************************************************************/
    /**
    * References for single chapter books may sometimes be misread.  The present
    * method canonicalises them as follows :-
    *
    * - It does something only if the reference passed to it has its book
    *   element set and the book is indeed a single chapter book.
    *
    * - Further, it does something only if the reference has a chapter value and
    *   no verse value.
    *
    * - In this case, it turns the chapter number into the verse number and sets
    *   the chapter number to 1.  Thus Oba 2 becomes Oba 1:2.
    *
    * - The reference passed as argument is updated in situ.
    *
    * @param ref Reference to be checked and updated if necessary.
    */

    fun canonicaliseRefsForSingleChapterBooks (ref: Ref)
    {
      if (ref.hasB() && !ref.hasV() && BibleAnatomy.isSingleChapterBook(ref.getB()))
      {
        val x = ref.getC()
        ref.setC(1)
        ref.setV(x)
      }
    }


    /****************************************************************************/
    /**
    * Given b / c/ v /s, returns the index for that element within the internal
    * arrays.  This is public not to make it possible for the outside world to
    * access those arrays directly, but in case anything wants to set up its own
    * arrays which somehow mirror the internal structure.
    *
    * @param sel: b / c / v s
    * @return Index.
    */

    fun getIndex (sel: Char): Int
    {
      return when (sel)
      {
        'B', 'b' -> C_BookIx
        'C', 'c' -> C_ChapterIx
        'V', 'v' -> C_VerseIx
        else     -> C_SubverseIx
      }
    }


    /****************************************************************************/
    /**
    * Given b / c/ v /s, returns the index for that element within the internal
    * arrays.  This is public not to make it possible for the outside world to
    * access those arrays directly, but in case anything wants to set up its own
    * arrays which somehow mirror the internal structure.
    *
    * @param sel: b / c / v s
    * @return Index.
    */

    fun getIndex (sel: String): Int
    {
      return getIndex(sel[0])
    }


    /****************************************************************************/
    /**
     * Takes an index into one of the internal arrays and returns the corresponding
     * b / c/ v/ s.
     *
     * @param ix Index
     * @return b / c / v / s
     */

     fun getKeyAsChar (ix: Int): Char
     {
       return when (ix)
       {
         0 -> 'b'
         1 -> 'c'
         2 -> 'v'
         else -> 's'
       }
     }


   /****************************************************************************/
   /**
    * Takes an index into one of the internal arrays and returns the corresponding
    * b / c/ v/ s.
    *
    * @param ix Index
    * @return b / c / v / s
    */

   fun getKeyAsString (ix: Int): String
   {
     return getKeyAsChar(ix).toString()
   }


   /****************************************************************************/
   /**
    * Determines whether two references are to the same chapter.
    */

   fun sameChapter (refA: Ref, refB: Ref): Boolean
   {
     return refA.getB() == refB.getB() && refA.getC() == refB.getC()
   }


    /****************************************************************************/
    private fun rdCommon (reader: RefFormatHandlerReaderVernacular, text: String, context: Ref?, resolveAmbiguitiesAs: String?): Ref
    {
      return try
      {
        val x = reader.readEmbedded(text, context, resolveAmbiguitiesAs)
        if (1 != x.size || x[0] !is RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection) throw StepException("")
        val rc = (x[0] as RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection).rc
        if (!rc.isSingleReference()) throw StepException("")
        rc.getLowAsRef()
      }
      catch (e: Exception)
      {
        throw StepException("Cannot parse as single ref: $text")
      }
    }


    /****************************************************************************/
    /* A late arrival on the scene -- I never anticipated having to read OSIS
       refs because OSIS was an _output_ of the processing, not an input.
       However, late in the day we've discovered it may be necessary to parse
       OSIS files in support of extended tagging.

       The rationale for the existence of this as a separate method is the same
       as that for rdUsxInternal, qv.

       OSIS references look to be rather simpler than USX ones, because -- so
       long as people play by the rules -- they are guaranteed to be
       complete (ie no defaulting from context. */

    private fun rdOsisInternal (theText: String, context: Ref?, resolveAmbiguitiesAs: String?): Ref
    {
      var elts = theText.trim().split("[.!]".toRegex())
      val b = BibleBookNamesOsis.nameToNumber(elts[0])
      val c = if (elts.size < 2) C_DummyElement else Integer.parseInt(elts[1])
      val v = if (elts.size < 3) C_DummyElement else Integer.parseInt(elts[2])
      val s = if (elts.size < 4) C_DummyElement else convertRepeatingStringToNumber(elts[3], 'a', 'z')
      return rd(b, c, v, s)
    }


    /****************************************************************************/
    /* This method came into existence to some extent because of an oversight on
       my part.  I have gone to a huge amount of trouble to provide generic code
       to cater for references of whatever form (USX, OSIS or any vernacular at
       all).  However, this flexibility necessarily comes at a possibly
       significant cost in terms of processing efficiency.

       It then occurred to me that I do not really require so much flexibility
       with USX references, because the USX standard constrains the
       manner in which they are used pretty tightly -- which seemed to suggest
       there would be value in having a specific implementation for USX
       references.  This is where the code below was born.

       Unfortunately, I have since realised that things aren't that simple after
       all, because I pre-process references in the reversification data to look
       like USX, and the constraints in the USX standard are not observed in the
       reversification data.

       All of which is to say that although the code below is still probably
       more efficient than my generic code, it is not quite as efficient as I
       had first hoped.

       Note that at particular points in the proceedings, you may not know whether
       you will be parsing a single reference or a range.  In these cases, use
       RefRange.rdUsx or RefCollection.rdUsx instead.

       One other thing.  Ever since time immemorial, I have assumed that it is
       ok to have a range which covers a number of verses in the same chapter;
       and ok to have a range which covers a number of subverses in the same
       verse.  These constraints were aimed at making it possible to expand
       a range into the individual references which make it up.  It's easy to
       expand 1-10 into ten verses, ditto a-c into three subverses.

       Cross-chapter ranges are more difficult -- you have to know what the
       maximum verse number is in each chapter, but I think I concluded some
       time ago that I normally _would_ know, and that therefore there wasn't
       a major problem there.

       But cross-verse subverse ranges -- 10c-11f, or 10-11b, or 10c-11 --
       remained a no-no.  Even now, I don't think I've done anything to
       make it possible to expand these out, but I've decided to relent
       here, and make it possible to _have_ them, in the hope that I'm not
       then asked to do much with them.
     */

    private fun rdUsxInternal (theText: String, context: Ref?, resolveAmbiguitiesAs: String?): Ref
    {
      /************************************************************************/
      //Dbg.d(theText, "ZEC 4:9-10a")



      /************************************************************************/
      val res = Ref()
      var elts = theText.replace("\\s+".toRegex(), " ").trim().split("[: ]".toRegex())
      var ambiguous = false
      var b: String? = null
      var c: String? = null
      var v: String? = null
      var s: String? = null
      var vs: String? = null



      /************************************************************************/
      /* If we have three elements, then we know which is which. */

      if (3 == elts.size)
      {
         b = elts[0]
         c = elts[1]
         vs = elts[2]
      }




      /************************************************************************/
      /* If we have two elements, then again things fall into place -- a
         different place, depending upon whether or not we have a colon. */

      else if (2 == elts.size)
      {
        if (theText.contains(":"))
        {
          c = elts[0]
          vs = elts[1]
        }
        else
        {
          b = elts[0]
          c = elts[1]
        }
      }



      /************************************************************************/
      /* If we have only one element, things are more complicated -- we need to
         investigate further because at this level it could be anything, but
         there are ways of narrowing it down further. */

      else
      {
        if (elts[0].matches("(?i).\\w\\w\\w.".toRegex()))
          b = elts[0]
        else if (elts[0].matches("[A-Z]".toRegex()))
          c = elts[0]
        else if (elts[0].matches("[a-z]+".toRegex()))
          s = elts[0]
        else if (elts[0].matches("[0-9]+".toRegex()))
          { c = elts[0]; ambiguous = true; }
        else
          vs = elts[0]
      }



      /************************************************************************/
      if (ambiguous && null != context && context.hasV())
      {
        ambiguous = false
        v = c
        c = null
      }



      /************************************************************************/
      if (ambiguous)
      {
        if (null == resolveAmbiguitiesAs) throw StepException("Ambiguous reference: $theText")
        if ("v".equals(resolveAmbiguitiesAs!!, ignoreCase = true)) { v = c; c = null }
      }



      /************************************************************************/
      /* We had something which looks like a combined verse and subverse.  See
         if we can split them out. */

      if (null != vs)
      {
        val vx = vs.replace("[a-z]+".toRegex(), "")
        val sx = vs.replace("\\d+".toRegex(), "")
        if (vx.isNotEmpty()) v = vx
        if (sx.isNotEmpty()) s = sx
      }



      /************************************************************************/
      try
      {
        if (null != b)  { res.setElementSource('b', ElementSource.Explicit); res.setB(BibleBookNamesUsx.abbreviatedNameToNumber(b)) }
        if (null != c)  { res.setElementSource('c', ElementSource.Explicit); res.setC(c.toIntOrNull() ?: RefBase.C_AlphaChapterOffset + convertRepeatingStringToNumber(c, 'A', 'Z')) }
        if (null != v)  { res.setElementSource('v', ElementSource.Explicit); res.setV(v.toInt()) }
        if (null != s)  { res.setElementSource('s', ElementSource.Explicit); res.setS(convertRepeatingStringToNumber(s, 'a', 'z')) }
        res.mergeFromOther(context)
        canonicaliseRefsForSingleChapterBooks(res)
        return res
      }
      catch (_: Exception)
      {
        throw StepException("Could not read as USX ref: $theText")
      }
    }


    /****************************************************************************/
    private fun refKeyFromElts (elts: IntArray): RefKey
    {
        var res: RefKey = elts[C_BookIx].toLong()
        res = res * C_Multiplier + elts[C_ChapterIx]
        res = res * C_Multiplier + elts[C_VerseIx]
        res = res * C_Multiplier + elts[C_SubverseIx]
        return res
    }

  
    /****************************************************************************/
    private fun refKeyFromElts (elts: IntArray, theSelectors: String): RefKey
    {
      var selectors = theSelectors
      selectors = selectors.lowercase()
      val revisedElts = IntArray(elts.size)
      for (i in elts.indices) revisedElts[i] = if (selectors.contains(C_SelectorsAsStrings[i])) elts[i] else C_DummyElement
      return refKeyFromElts(revisedElts)
    }
  
  
    /****************************************************************************/
    private const val C_SubverseFactor = 1
    private const val C_VerseFactor    = C_Multiplier * C_SubverseFactor
    private const val C_ChapterFactor  = C_VerseFactor * C_Multiplier
    private const val C_BookFactor     = C_ChapterFactor * C_Multiplier
  
  
    /****************************************************************************/
    const val C_BookIx: Int     = 0
    const val C_ChapterIx: Int  = 1
    const val C_VerseIx: Int    = 2
    const val C_SubverseIx: Int = 3

  
  
    /****************************************************************************/
    private val C_SelectorsAsStrings = arrayOf("b", "c", "v", "s")
  }
}