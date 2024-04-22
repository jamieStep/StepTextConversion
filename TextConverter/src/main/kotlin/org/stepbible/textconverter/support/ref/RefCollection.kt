/****************************************************************************/
package org.stepbible.textconverter.support.ref

import org.stepbible.textconverter.support.stepexception.StepException



/****************************************************************************/
/**
* Reference collection.
*
* A reference collection is a collection of [RefCollectionPart]s, each of
* which may be either a single reference or a reference range.
*/


class RefCollection: RefBase
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
   * Default constructor.
   */

  constructor (): super()


  /****************************************************************************/
  /**
   * Create from single ref collection part (which can be either a Ref or a
   * RefRange).
   *
   * @param refCollectionPart Reference to be stored in collection.
   */

  constructor (refCollectionPart: RefCollectionPart)
  {
    m_Elements.add(refCollectionPart)
  }


  /****************************************************************************/
  /**
   * Create from a list of RefCollectionParts or RefKeys.  I have to cater for
   * both of these in this one method because the List<RefCollectionPart> and
   * List<RefKey> is the same as regards the signature when it comes to
   * polymorphism.
   *
   * @param refCollectionPartsOrRefKeys Items to be stored.
   */

  constructor (refCollectionPartsOrRefKeys: List<Any>)
  {
  if (refCollectionPartsOrRefKeys[0] is RefKey)
    refCollectionPartsOrRefKeys.forEach{ m_Elements.add(Ref.rd(it as RefKey)) }
  else
    add(refCollectionPartsOrRefKeys as List<RefCollectionPart>)
  }


  /****************************************************************************/
  /**
   * Copy constructor.
   *
   * @param other Range to be copied.
   */

  constructor (other:RefCollection)
  {
    add(other)
  }


  /****************************************************************************/
  /**
   * Adds an element to the collection.
   *
   * @param elt Element to add.
   * @return This collection
   */

  fun add (elt: RefCollectionPart): RefCollection
  {
     m_Elements.add(elt)
     invalidateCache()
     return this
  }


  /****************************************************************************/
  /**
   * Adds a list of RefCollectionParts to the collection.
   *
   * @param elts Elements to add.
   * @return This collection
   */

  fun add (elts: List<RefCollectionPart>): RefCollection
  {
     m_Elements.addAll(elts)
     invalidateCache()
     return this
  }


  /****************************************************************************/
  /**
   * Adds all the elements from another collection to the present one.
   *
   * @param other Other reference collection.
   * @return This collection
   */

  fun add (other: RefCollection): RefCollection
  {
     other.m_Elements.forEach { m_Elements.add(it) }
     invalidateCache()
     return this
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
    throw StepException("Not expecting to format collections as OSIS")
  }


  /******************************************************************************/
  /* USX references always have to be written out in full, so it's convenient to
     have a specific method for that. */

  override fun formatMeAsUsx (format:String, context: Ref?): String
  {
    return getElements().joinToString("; "){ it.toString(format, context) } // I'm not expecting to do this, but may as well supply the functionality anyway.
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
       m_Elements.forEach { x:RefCollectionPart -> m_SavedRefKeys.addAll(x.getAllAsRefKeys()) }

     return m_SavedRefKeys
  }


  /****************************************************************************/
  /**
   * Converts all contents to their refKeys and returns the whole lot.
   *
   * @return Refs.
   */

  override fun getAllAsRefs (): List<Ref>
  {
    return getAllAsRefKeys().map{ Ref.rd(it) }
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
    return m_Elements.size
  }


  /****************************************************************************/
  /**
   * Returns the underlying elements.  Note that this is not the same as the
   * underlying refs -- a range is returned as a single element.
   *
   * @return Elements.
   */

  fun getElements (): List<RefCollectionPart>
  {
    return m_Elements
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
    return if (m_Elements.isEmpty()) Ref() else m_Elements[0].getFirstAsRef()
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
    return if (m_Elements.isEmpty()) Ref() else m_Elements.last().getHighAsRef()
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

  override fun getReferenceCount(): Int
  {
    return getAllAsRefKeys().size
  }


  /****************************************************************************/
  /**
  * Returns an indication of whether the collection is empty.
  *
  * @return True if empty.
  */

  fun isEmpty (): Boolean
  {
    return m_Elements.isEmpty()
  }


  /****************************************************************************/
  /**
   * Returns an indication of the type of thing we have here.  If the collection
   * has just one entry, we say it is not a collection.  Otherwise it is a RefRange or a
   * Ref depending upon whether it contains just a single element of the given
   * type.
   *
   * @return Type.
   */

  fun isCollection (): Boolean { return 1 != getElements().size }
  fun isRange (): Boolean { return !isCollection() && getElements()[0] is RefRange }
  fun isSingleReference (): Boolean { return !isCollection() && !isRange() }


  /****************************************************************************/
  /**
   * Simplifies the collection by looking for consecutive verses (or subverses)
   * and replacing them by an equivalent range.  In determining the verses
   * which could make up a range, the method uses the vernacular verse
   * structure as it stands at the time of the call.
   *
   * May throw StepException if being asked to handle unreasonable collections
   * -- for example ranges which cross chapter boundaries.
   */

  fun simplify ()
  {
    /**************************************************************************/
    val refKeys: List<RefKey> =
      try
      {
        getAllAsRefKeys()
      }
      catch (e:Exception)
      {
        return
      }


    /**************************************************************************/
    if (refKeys.isEmpty()) return



    /**************************************************************************/
    val n = refKeys.size
    var ixLow = 0
    m_Elements.clear()



    /**************************************************************************/
    while (ixLow < n)
    {
      val refKey = refKeys[ixLow]
      val lastWasSubVerse = Ref.getS(refKey) != C_DummyElement
      var ix = ixLow
      var ixHigh = ixLow
      var prevKey = refKeys[ixLow]



      /************************************************************************/
      /* At the end of this loop, ixHigh points either to the same place as
         ixLow (in which case we want to add an individual reference to
         m_Elements), or else to somewhere above ixLow (in which case we want to
         add a range). */

      while (true)
      {
        ++ix
        if (ix >= n) break

        val thisKey = refKeys[ix]
        val thisIsSubVerse = Ref.getS(thisKey) != C_DummyElement
        if (thisIsSubVerse != lastWasSubVerse) break


        val diff =
          if (lastWasSubVerse)
            getBibleStructure()!!.getSubverseDifference(prevKey, thisKey)
          else
            getBibleStructure()!!.getVerseDifference(prevKey, thisKey)
        if (diff != 1) break

        prevKey = thisKey
        ixHigh = ix
      } // while



      /************************************************************************/
      if (ixLow == ixHigh) // Individual reference.
      {
        val  ref = Ref.rd(refKeys[ixLow])
        m_Elements.add(ref)
      }
      else  // Range.
      {
        val refLow = Ref.rd(refKeys[ixLow])
        val refHigh = Ref.rd(refKeys[ixHigh])
        val ref = RefRange(refLow, refHigh)
        m_Elements.add(ref)
      }

      ixLow = ixHigh + 1
    } // while (ixLow < n)



    /**************************************************************************/
    /* Clear out anything which may have been messed up by this change. */

    invalidateCache()
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun invalidateCache ()
  {
    m_SavedRefKeys.clear()
  }


  /****************************************************************************/
  private val m_Elements:MutableList<RefCollectionPart> = ArrayList()
  private val m_SavedRefKeys: MutableList<RefKey> = ArrayList()




  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  /******************************************************************************/
  companion object
  {
    /**************************************************************************/
    /* The various read methods.  We probably shouldn't need to read USX
       collections if people follow the rules about not having collections in
       cross-references, but they don't.

       We may well need to read vernacular references, and at one stage I was
       assuming we'd have to handle that in RefFormatHandlerVernacular, since
       we'd have to assume they would be embedded in a larger text string, and
       therefore couldn't be returned as a simple collection.  However, I've
       now come across a text where we have straightforward vernacular
       references, so I've provided a wrapper to RefFormatHandlerVernacular
       calls here to make this more straightforward.

       The version of rd with the extensive parameter list is just a shorthand
       version of rdUsx. */

    fun rd           (text: String, dflt: Ref? = null, resolveAmbiguitiesAs: String? = null): RefCollection { return rdUsx(text, dflt, resolveAmbiguitiesAs)  }
    fun rdOsis       (text: String, dflt: Ref? = null, resolveAmbiguitiesAs: String? = null): RefCollection { return rdOsisInternal(text, dflt, resolveAmbiguitiesAs)  }
    fun rdUsx        (text: String, dflt: Ref? = null, resolveAmbiguitiesAs: String? = null): RefCollection { return rdUsxInternal(text, dflt, resolveAmbiguitiesAs)  }
    fun rdVernacular (text: String, dflt: Ref? = null, resolveAmbiguitiesAs: String? = null): RefCollection { return rdVernacularInternal(text, dflt, resolveAmbiguitiesAs)  }

    fun rd (ref: RefCollectionPart): RefCollection { return RefCollection(ref) }
    fun rd (refs: List<Any>): RefCollection { return RefCollection(refs) } // May be List<RefKey> or List<RefCollectionPart>.


    /**************************************************************************/
    /* Specialist reader for OSIS.  In fact, collections are not permitted in
       osisIDs or in osisRefs, so this serves purely as a do-very-little
       wrapper. */

    private fun rdOsisInternal (text: String, context: Ref?, resolveAmbiguitiesAs: String?): RefCollection
    {
      val rng = RefRange.rdOsis(text, context=context, resolveAmbiguitiesAs=resolveAmbiguitiesAs)
      return if (rng.isSingleReference()) RefCollection(rng.getLowAsRef()) else RefCollection(rng)
    }


    /**************************************************************************/
    /* Specialist reader for USX.  Because officially USX format is used only
       as laid down in the USX spec (which does not allow for collections), I'd
       thought that it would be unnecessary to provide for a collection reader.
       However, I'd forgotten that I was pre-processing references in the
       reversification data to make them look like USX, and that data _may_
       contain collections.

       This notwithstanding, it's probably still worth having special handling
       for USX collections, if only for the sake of mirroring what I'm doing
       on ranges and individual references, where there are definite efficiency
       benefits from having special handling.

       !!!!! I'm not 100% sure about the context and resolveAmbiguities
       processing here, but it will do until we know it won't.
    */

    private fun rdUsxInternal (text: String, context: Ref?, resolveAmbiguitiesAs: String?): RefCollection
    {
      val bits = text.split("(\\s*,\\s*)|(\\s*;\\s*)".toRegex())
      var dfltRef = context
      var resolveAmbiguities = resolveAmbiguitiesAs
      val res = RefCollection()

      for (i in bits.indices)
      {
        if (bits[i].contains("-"))
        {
          val rng = RefRange.rdUsx(bits[i], context=dfltRef, resolveAmbiguitiesAs=resolveAmbiguities)
          res.add(rng)
          dfltRef = rng.getHighAsRef()
          resolveAmbiguities = null
        }
        else
        {
          val ref = Ref.rdUsx(bits[i], context=dfltRef, resolveAmbiguitiesAs=resolveAmbiguities)
          res.add(ref)
          dfltRef = ref
          resolveAmbiguities = null
        }
      }

      return res
    }


    /**************************************************************************/
    /* Specialist reader for vernacular text -- and more particularly for
       non-embedded collections.  (Vernacular text may contain collections
       embedded within larger text strings, and indeed often will do so, but
       to handle those, you need to rely upon RefFormatHandlerReaderVernacular
       directly.  Here I assume something simpler. */

    private fun rdVernacularInternal (text: String, context: Ref?, resolveAmbiguitiesAs: String?): RefCollection
    {
      val x = RefFormatHandlerReaderVernacular.readEmbedded(text, context, resolveAmbiguitiesAs)
      if (x.size != 1 || x[0] !is RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection)
        throw StepException("Invalid vernacular reference: $text.")
      else
        return (x[0] as RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection).rc
    }
  }
}