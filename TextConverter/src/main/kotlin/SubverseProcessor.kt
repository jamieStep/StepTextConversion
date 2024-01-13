package org.stepbible.textconverter

import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.set
import org.stepbible.textconverter.support.ref.RefRange
import org.w3c.dom.Node

/******************************************************************************/
/**
* Subverses are a bit problematical.  They may turn up in a number of different
* contexts, but we can't necessarily always cope with them -- the Crosswire
* version of osis2mod does not do so, although our version does.  We may or may
* not need to treat them specially, therefore.  This class handles this.
*
* As regards the contexts in which I think we need to cope with them ...
*
* - They may turn up as verse references -- Gen 1:1a.
*
* - They may turn up within the references to elided verses --
*   Gen 1:1a - Gen 1:1z.
*
* - They may turn up within the ref tags, in the loc attribute, which indicates
*   where the cross-reference represented by the tag should point.
*
* @author ARA "Jamie" Jamieson
*/

object SubverseProcessor
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Deals with the loc attribute of ref tags, and in particular the issue of
  * what should happen if it contains subverses.
  *
  * If we are using the STEP version of osis2mod, it's ok for it to contain
  * subverses, because they are supported, and there is therefore nothing to
  * do.
  *
  * If we are using the Crosswire version, I'm not so sure.  Crosswire
  * doesn't support subverses, but I'm not sure whether it would accept
  * subverses in the loc attribute anyway, and simply treat them as being
  * the owning verse -- in which case, again, I needn't do anything.
  *
  * I'm assuming, however, that things would go wrong if the subverses were
  * retained in the loc attribute, and therefore simply drop the subverse so
  * that we end up pointing at the owning verse.
  *
  * @param refs ref tags to be processed.
  * @return True if any changes have been applied.
  */

  fun canonicaliseRefsConvertCrossVerseSubverseRangesToVersesIfNecessary (refs: List<Node>): Boolean
  {
    /**************************************************************************/
    if (UsxA_Osis2modInterface.instance().supportsSubverses())
      return false



    /**************************************************************************/
    var res = false

    fun processLoc (node: Node)
    {
      val refRange = RefRange.rdUsx(node["loc"]!!)
      var refLow = refRange.getLowAsRef()
      var refHigh = refRange.getHighAsRef()

      if (!refLow.hasS() && !refHigh.hasS())
        return

      res = true

      if (refLow.hasS())
      {
        refLow.clearS()
        if (refRange.isSingleReference())
        {
          node["loc"] = refLow.toString()
          return
        }
      }

      if (refHigh.hasS())
        refHigh.clearS()

      node["loc"] = RefRange(refLow, refHigh).toString("bcv-bcv") // $$$ Is that right for the format string?  I want both start and end as bcv.
    }

    /**************************************************************************/
    refs.forEach { processLoc(it) }
    return res
  }



  /****************************************************************************/
  /**
  * 
*/
  fun dealWithSubversesAppearingAsVerseReferences ()
  {

  }
}