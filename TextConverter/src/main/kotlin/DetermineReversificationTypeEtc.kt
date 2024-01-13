/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.TextStructureEnhancedUsx
import org.stepbible.textconverter.support.bibledetails.BibleStructuresSupportedByOsis2mod
import org.stepbible.textconverter.support.bibledetails.TextStructureUsxForUseWhenAnalysingInput
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.configdata.ConfigData


/******************************************************************************/
/**
* Controls the whole of the processing.
*
* @author ARA "Jamie" Jamieson
*/

object DetermineReversificationTypeEtc
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process () = doIt()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Hmm ... this gets rather complicated, as will shortly be apparent. */

  private fun doIt ()
  {
    /**************************************************************************/
    val versificationSchemeWhichWouldBeUsedByCrosswireOsis2mod = getVersificationSchemeWhichWouldBeUsedByCrosswireOsis2mod()



    /**************************************************************************/
    fun reversificationTypeNeeded (): String
    {
      val schemeEvaluation = VersificationSchemesEvaluator_InputUsxOrUsxA.evaluateSingleScheme(versificationSchemeWhichWouldBeUsedByCrosswireOsis2mod)
      return if (null == schemeEvaluation)
          "runtime" // We're using a bespoke scheme, in which case we do need reversification.  Runtime will do,
        else if (schemeEvaluation.booksMissingInOsis2modScheme > 0 || schemeEvaluation.versesMissingInOsis2modScheme > 0 || TextStructureEnhancedUsx.getBibleStructure().hasSubverses())
          "either"
        else
          "none"
    }



    /**************************************************************************/
    /* To begin with, reversification type.

       It is possible to attempt to force the reversification type by setting
       stepForceReversificationType to runTime, conversionTime, none or tbd.

       If forced to be runTime or conversionTime, the setting is always
       honoured, although a warning will be issued if the text would have been
       NRSV(A) compliant even without reversification.

       If forced to be none, it is an error if the text is not NRSV(A)
       compliant.

       (NRSV(A) compliant means that the text does not contain any verses not
       catered for by NRSV(A).  It is not a problem if the text _lacks_ verses
       required by NRSV(A).)

       If left as TBD (which is the default), the text is checked against
       NRSV(A). If compliant, it is as though None had been specified.  Otherwise
       it is as though runTime had been specified. */

    var reversificationType = (ConfigData["stepForceReversificationType"] ?: "tbd").lowercase()
    if (reversificationType.isEmpty()) reversificationType = "tbd"
    when (reversificationType)
    {
      "conversiontime" ->
      {
        if ("none" == reversificationTypeNeeded())
          Logger.warning("Conversion-time reversification specified (and honoured), but reversification not needed.")
      }

      "runtime" ->
      {
        if ("none" == reversificationTypeNeeded())
          Logger.warning("Run-time reversification specified (and honoured), but reversification not needed.")
      }

      "none" ->
      {
        if ("none" != reversificationTypeNeeded())
          Logger.error("Reversification prohibited but text requires it.")
      }

      "tbd" ->
      {
        when(reversificationTypeNeeded())
        {
          "none" ->
          {
            ConfigData["stepForceReversificationType"] = "none"
            Logger.info("Processing has decided no reversification is needed.")
          }

          "runtime", "either" -> // Prefer runtime reversification if it is needed at all
          {
            ConfigData["stepForceReversificationType"] = "runtime"
            Logger.info("Processing has decided to apply runtime reversification.")
          }
        }
      }
    }



    /**************************************************************************/
    /* Record relevant details. */

    ConfigData.delete("stepReversificationType")
    ConfigData["stepReversificationType"] = ConfigData["stepForceReversificationType"]!!.lowercase()
    val reversificationFootnoteLevel = ConfigData["stepReversificationFootnoteLevel"] ?: "basic"
    ConfigData.delete("stepReversificationFootnotesLevel")
    ConfigData["stepReversificationFootnotesLevel"] = reversificationFootnoteLevel



    /**************************************************************************/
    /* On to the versification scheme.

       If we have accepted 'None' for the reversification type, then we can
       go with whatever has been specified in the configuration data, or
       NRSV(A) if nothing has been specified.  We already have this information
       in versificationSchemeWhichWouldBeUsedByCrosswireOsis2mod.

       Otherwise, if conversiontime has been specified for the reversification
       type, then irrespective of anything which may have been stipulated, we
       want NRSV(A).

       And finally, if runtime has been specified, we will be making up our
       own name in due course, but can't do so yet because we have not
       established all the necessary information.  In this case,
       stepVersificationScheme remains undefined here. */

    ConfigData.delete("stepVersificationScheme")
    when (ConfigData["stepReversificationType"])
    {
      "none"           -> ConfigData["stepVersificationScheme"] = versificationSchemeWhichWouldBeUsedByCrosswireOsis2mod
      "conversiontime" -> ConfigData["stepVersificationScheme"] = BibleStructuresSupportedByOsis2mod.canonicaliseSchemeName("NRSV" + if ("a" in versificationSchemeWhichWouldBeUsedByCrosswireOsis2mod.lowercase()) "A" else "")
      "runtime"        -> ConfigData.delete("stepVersificationScheme")
    }



    /**************************************************************************/
    /* Determine what kind of osis2mod we are going to run -- Crosswire's or
       ours.

       A force-setting is available.  If this is not supplied, it is as though
       the force-setting were 'tbd.'

       If the force-setting is 'step', it is honoured, but a warning is issued
       if there appear to be no grounds for using our own version.  (I take
       grounds for using it as being either that we are applying runtime
       reversification, or that the text contains out-of-order verses.)
        If the setting is crosswire, it's an error if there are grounds for
       needing to use our version (see above); otherwise, the setting is
       honoured.

       If the setting is 'tbd', the choice is determined by whether there are
       grounds for needing our version or not. */

    fun groundsForUsingStepOsis2mod () = ConfigData["stepForceReversificationType"]!! !in "none.conversiontime" || TextStructureUsxForUseWhenAnalysingInput.getBibleStructure().versesAreInOrder()

    var osis2modType = (ConfigData["stepForceOsis2modType"] ?: "tbd").lowercase()
    if (osis2modType.isEmpty()) osis2modType = "tbd"
    when (osis2modType)
    {
      "step" ->
      {
        if (!groundsForUsingStepOsis2mod())
          Logger.warning("Use of STEP osis2mod stipulated (and honoured), but the usual grounds for requiring (ie reversification or out of order verses) it do not apply.")
      }

      "crosswire" ->
      {
        if (groundsForUsingStepOsis2mod())
          Logger.error("Use of Crosswire osis2mod stipulated, but text requires reversification, and Crosswire osis2mod won't work with that.")
      }

      "tbd" ->
      {
        if (groundsForUsingStepOsis2mod())
        {
          ConfigData["stepForceOsis2modType"] = "step"
          Logger.info("Processing has decided to use STEP osis2mod.")
        }
        else
        {
          ConfigData["stepForceOsis2modType"] = "crosswire"
          Logger.info("Processing has decided to use Crosswire osis2mod.")
        }
      }
    }



    /**************************************************************************/
    ConfigData.delete("stepOsis2modType")
    ConfigData["stepOsis2modType"] = ConfigData["stepForceOsis2modType"]!!
    UsxA_Osis2modInterface.instance().initialise()
    //TestController.setInstance()
  }


  /****************************************************************************/
  /* Returns the versification scheme which would be used if we were to run
     Crosswire's version of osis2mod.

     If no setting has been specified in the configuration data, we proceed as
     though NRSV had been specified.

     If the value is anything other than NRSV(A) or KJV(A) -- both of which
     exist in one form _with_ DC and one _without_, we take the value as-is,
     except for converting it to the canonical lowercase / upper case form
     required by osis2mod.

     If the value is either of NRSV(A) or KJV(A), we look to see whether DC
     books are actually involved (by seeing which books are available in the
     text and which might be created if reversification were applied) and
     convert the value to the appropriate form.

     Note that this method merely returns details of what the versification
     scheme might be should we choose to go with it.  It is down to the caller
     to decide whether or not to go with it. */

  private fun getVersificationSchemeWhichWouldBeUsedByCrosswireOsis2mod (): String
  {
    var versificationScheme = ConfigData["stepVersificationScheme"] ?: "NRSV"
    val existsAsBothWithAndWithoutDc= versificationScheme.uppercase().substring(0,3) in "KJV.NRSV"
    if (existsAsBothWithAndWithoutDc)
    {
      TextStructureUsxForUseWhenAnalysingInput.populateVerseStructure("Analysing input")
      val requiresDc = TextStructureUsxForUseWhenAnalysingInput.getBibleStructure().hasAnyBooksDc() || ReversificationData.reversificationTargetsDc()
      if (requiresDc && "A" !in versificationScheme)
        versificationScheme += "A"
      else if (!requiresDc)
        versificationScheme = versificationScheme.replace("A", "")
    }

    return BibleStructuresSupportedByOsis2mod.canonicaliseSchemeName(versificationScheme)
  }
}
