/******************************************************************************/
package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.processingelements.PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation
import org.stepbible.textconverter.utils.BibleStructure
import org.stepbible.textconverter.support.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.IssueAndInformationRecorder
import org.stepbible.textconverter.utils.ReversificationData


/******************************************************************************/
/**
* Determines reversification and osis2mod types.
*
* Should be run after we've made any salient non-reversification tweaks to the
* data, but obviously before reversification may become an issue.
*
* On exit, various configuration parameters are set up as follows:
*
* - stepReversificationType: "none, "runtime" or "conversiontime".
*
* - stepReversificationFootnoteLevel: "basic" or "academic"
*
* - stepVersificationScheme: For conversion time reversification, NRSV(A).
*     For no reversification, any scheme specified in the configuration data,
*     or else NRSV(A).  For runtime reversification, the parameter remains
*     unset here -- we need to make up our own name for the scheme, but aren't
*     yet in a position to do so.
*
* - stepOsis2modType: If specified as Crosswire or Step on the command line,
*     then that value, in lower case.  Otherwise forced to step if runtime
*     reversification has been selected, or step if not.
*
* @author ARA "Jamie" Jamieson
*/

object Osis_DetermineReversificationTypeEtc
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (bibleStructureUnderConstruction: BibleStructure) = doIt(bibleStructureUnderConstruction)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Hmm ... this gets rather complicated, as will shortly be apparent. */

  private fun doIt (bibleStructureUnderConstruction: BibleStructure)
  {
    /**************************************************************************/
    Dbg.reportProgress("Determining reversification requirements.")



    /**************************************************************************/
    /* We may derive certain details from the module name. */

    val isDefinedAsPublicOnly = "P" == ConfigData["stepTargetAudience"]!!
    val isDefinedAsStepOnly = "S" == ConfigData["stepTargetAudience"]!!
    val isDefinedAsHybrid = !(isDefinedAsStepOnly || isDefinedAsPublicOnly)
    val isDefinedAsPublicOrHybrid = isDefinedAsPublicOnly || isDefinedAsHybrid



     /**************************************************************************/
     /* Sort out reversification type and convert to canonical form. */

    var reversificationType = ConfigData["stepVersificationType"]
    if (null != reversificationType)
    {
      ConfigData.delete("stepReversificationType")
      ConfigData.put("stepReversificationType", reversificationType.lowercase(), force = true)
    }



    /**************************************************************************/
    /* If we're given a versification scheme, force it to canonical form. */

    var versificationScheme = ConfigData["stepVersificationScheme"]
    if (null != versificationScheme)
    {
      versificationScheme = VersificationSchemesSupportedByOsis2mod.canonicaliseSchemeName(versificationScheme)
      ConfigData.delete("stepVersificationScheme"); ConfigData.put("stepVersificationScheme", versificationScheme, force = true)
    }



    /**************************************************************************/
    /* Regardless of what we're being called on to do, it's useful to check
       whether NRSV(A) fits as a versification scheme, because we may want to
       default to it, or suggest it as an alternative to the scheme actually
       selected. */

    val nrsvScheme = "NRSV" + if (bibleStructureUnderConstruction.hasAnyBooksDc()) "A" else ""
    val nrsvEvaluation = PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.evaluateSingleScheme(nrsvScheme, bibleStructureUnderConstruction)
    val nrsvDeviationType = nrsvEvaluation.getDeviationType()



    /**************************************************************************/
    /* If we're doing conversion-time reversification, then NRSV(A) is
       definitely the right scheme -- and we warn if anything else has been
       specified. */

    if ("conversiontime" == reversificationType)
    {
      if (null != versificationScheme && versificationScheme != nrsvScheme)
        Logger.warning("Forcing versification scheme to $nrsvScheme because run-time versification guarantees a text which fits that.")

      ConfigData.delete("stepVersificationScheme"); ConfigData.put("stepVersificationScheme", nrsvScheme, force = true)
      versificationScheme = nrsvScheme
    }



    /**************************************************************************/
    /* For STEP modules, force the reversification type to 'none' if the text
       fits NRSV(A), and otherwise force it to 'runtime'. */

    if (isDefinedAsStepOnly)
    {
      when (nrsvEvaluation.getDeviationType())
      {
        PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.VersificationDeviationType.BAD ->
        {
          if ("runtime" != reversificationType)
            Logger.warning("Text does not fit NRSV(A), so forcing stepReversificationType to 'runtime'.")
          ConfigData.delete("stepReversificationType"); ConfigData.put("stepReversificationType", "runtime", force = true)
        }

        else ->
        {
          if (null != reversificationType && "none" != reversificationType)
            Logger.warning("Text fits NRSV(A), so forcing stepReversificationType to 'none'.")
          ConfigData.delete("stepReversificationType"); ConfigData.put("stepReversificationType", "none", force = true)

          if (nrsvScheme != versificationScheme)
          {
            if (null != versificationScheme)
              Logger.warning("Forcing scheme to $nrsvScheme, since that fits.")
            ConfigData.delete("stepVersificationScheme"); ConfigData.put("stepVersificationScheme", nrsvScheme, force=true)
            versificationScheme = nrsvScheme
          }
        }
      }
    }


    /**************************************************************************/
    /* Another go at forcing reversification type to fit the circumstances ...

       - If there is applicable reversification data, then 'none' is not
         acceptable ... Or is it?

       - Otherwise, if we're creating a public-facing module, we must force
         conversionTime.

       - Note that if we're creating a STEP-only module, anything will do
         (except that 'none' is no good if there's applicable reversification
         data): we may have a text which fits NRSVA perfectly well, and be
         creating a STEP-only module merely because we need to apply
         encryption. */

    val nReversificationDataRows = if (PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.VersificationDeviationType.BAD != nrsvDeviationType)
                                     0
                                   else
                                     ReversificationData.getReversificationMappings().size
    if (0 == nReversificationDataRows)
    {
      if ("none" != reversificationType)
      {
        if (null != reversificationType)
          Logger.info("Forcing stepReversificationType = 'none' because no reversification rows apply or because the text fits NRSV.")
        ConfigData.delete("stepReversificationType"); ConfigData.put("stepReversificationType", "none", force = true)
       }
    }

    else // We have applicable reversification rows.
    {
      if (isDefinedAsPublicOrHybrid)
      {
        if ("conversiontime" != reversificationType)
        {
          Logger.info("Forcing stepReversificationType = 'conversionTime' because reversification rows apply and this module has been specified as being publicly available.")
          ConfigData.delete("stepReversificationType"); ConfigData.put("stepReversificationType", "conversionTime", force = true)
        }
      }

      else // isDefinedAsStepOnly
      {
        if ("runtime" != reversificationType)
       {
          Logger.info("Forcing stepReversificationType = 'runTime' because reversification rows apply and this module has been specified as being STEP-only.")
          ConfigData.delete("stepReversificationType"); ConfigData.put("stepReversificationType", "runTime", force = true)
        }
      }
    }



    /**************************************************************************/
    /* Probably belt and braces, but so what ... */

    if (null == versificationScheme && isDefinedAsPublicOrHybrid)
      throw StepException("For a public module, either the text must fit NRSV(A) -- which this text does not -- or you must overtly indicate a versification scheme.")

    if (isDefinedAsPublicOrHybrid && ConfigData.getAsBoolean("stepEncryptionRequired", "no"))
      throw StepException("Can't specify encryption on a public or hybrid module.")

    if ("conversiontime" == ConfigData["stepReversificationType"] && !ConfigData["stepVersificationScheme"]!!.startsWith("NRSV"))
      throw StepException("This run is applying conversion-time reversification, which generates an NRSV(A)-compliant module, but the versification scheme is not specified as NRSV(A).")

    if (isDefinedAsStepOnly && !ConfigData.getAsBoolean("stepEncryptionRequired", "no") && "runtime" != ConfigData["stepReversificationType"])
      throw StepException("This can be made as a public module, since neither runtime reversification nor encryption is needed.")

    if (isDefinedAsStepOnly && null != versificationScheme && "runtime" == ConfigData["stepReversificationType"])
      throw StepException("This is a STEP-only module to which you are not applying reversification.  This is acceptable only if the text fits NRSV(A), and it does not.")

      if ("runtime" == ConfigData["stepReversificationType"]!! && null != ConfigData["stepVersificationScheme"])
        Logger.warning("Ignoring the versification scheme setting, because runtime reversification uses its own ad hoc scheme.")



    /**************************************************************************/
    /* If we have a versification scheme, then there are certain additional
       things we need to deal with.  However, we may not -- if we have runtime
       reversification, we determine a name for the scheme later. */

    if (null != versificationScheme)
    {
      val selectedSchemeEvaluation = if (versificationScheme == nrsvScheme) nrsvEvaluation else PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.evaluateSingleScheme(versificationScheme, bibleStructureUnderConstruction)
      if (isDefinedAsPublicOnly &&
          ConfigData["stepVersificationScheme"]!!.startsWith("NRSV") &&
          PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.VersificationDeviationType.BAD != selectedSchemeEvaluation.getDeviationType())
        throw StepException("This can can be turned into a hybrid module, since it fits with NRSV.")

      if (isDefinedAsPublicOrHybrid && PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.VersificationDeviationType.BAD == selectedSchemeEvaluation.getDeviationType())
      {
        Logger.warning(selectedSchemeEvaluation.text!!)
        IssueAndInformationRecorder.setDivergencesFromSelectedVersificationScheme(selectedSchemeEvaluation.text!!)
        ConfigData.addDetailsOfDerivedWork(ConfigData["stepStandardWordingForDerivedWorkWeHaveChangedVersification"]!!)
      }
    }


    /**************************************************************************/
    /* Deal with reversification footnote level if necessary. */

    reversificationType = ConfigData["stepReversificationType"]!!.lowercase()
    if ("none" != reversificationType && null == ConfigData["stepReversificationFootnoteLevel"]?.lowercase())
    {
      Logger.info("stepReversficationFootnoteLevel not specified.  Assuming 'basic'.")
      ConfigData["stepReversificationFootnoteLevel"] = "basic"
    }



    /**************************************************************************/
    /* Decide what version of osis2mod is required -- our own with run-time
       reversification, or Crosswire for everything else. */

    when (reversificationType)
    {
      "runtime" -> { ConfigData.delete("stepOsis2modType"); ConfigData.put("stepOsis2modType", "step",      force = true) }
      else      -> { ConfigData.delete("stepOsis2modType"); ConfigData.put("stepOsis2modType", "crosswire", force = true) }
    }



    /**************************************************************************/
    /* For Crosswire osis2mod, the STEP software version must be at least 1.
       For STEP osis2mod, it must be at least 2. */

    val minStepSoftwareVersionRequired = if ("runtime" == reversificationType) 2 else 1
    val stepSoftwareVersionRequired = ConfigData["stepSoftwareVersionRequired"]?.toInt() ?: 1
    ConfigData.delete("stepSoftwareVersionRequired"); ConfigData.put("stepSoftwareVersionRequired", minStepSoftwareVersionRequired.toString(), force=true)
    if (stepSoftwareVersionRequired < minStepSoftwareVersionRequired)
    {
      ConfigData.delete("stepSoftwareVersionRequired"); ConfigData.put("stepSoftwareVersionRequired", minStepSoftwareVersionRequired.toString(), force=true)
      Logger.info("Forcing stepSoftwareVersionRequired = '$minStepSoftwareVersionRequired'")
    }



    /**************************************************************************/
    Logger.info("Versification scheme is ${versificationScheme ?: "STEP-internal"}.")
    Logger.info("ReversificationType is $reversificationType")
    Logger.info("osis2mod type is ${ConfigData["stepOsis2modType"]!!}, with STEP software version specified as $stepSoftwareVersionRequired.")
    Logger.info("Target audience is ${ConfigData["stepTargetAudience"]!!}.")



    /**************************************************************************/
    Osis_Osis2modInterface.instance().initialise()
  }
}
