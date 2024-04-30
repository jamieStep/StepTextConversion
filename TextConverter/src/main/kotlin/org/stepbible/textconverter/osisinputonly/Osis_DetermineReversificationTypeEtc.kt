/******************************************************************************/
package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.processingelements.PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation
import org.stepbible.textconverter.utils.BibleStructure
import org.stepbible.textconverter.support.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
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
    /* If the root folder name contains _public, we're creating a public module.
       Otherwise we are creating a STEP-internal module.

       If we are creating a public module:

       1. An osis2mod versification scheme must have been overtly specified.

       2. Encryption must not have been requested.

       3. Reversification type may optionally have been specified (the default,
          if not specified, is 'none').  It must be either 'none' or
          'conversionTime'.

       4. Reversification may be applied, as determined by the reversification
          type (but obviously if applied will be conversion-time
          reversification).

       5. We will use the Crosswire version of osis2mod.

       6. As a corollary of 5), version 1 of the STEP software will suffice.

       1-3 are checks which are applied here.  4-6 are settings which are forced
       here.

       In addition to the above, if crating a public module and the selected
       scheme is NRSV(A), and if the text fits NRSV(A) exactly, we will not
       both to generate a STEP-only version.
    */

    var targetAudience = ConfigData["stepTargetAudience"]!!
    if ("P" in targetAudience)
    {
      // Validation.
      var versificationScheme = ConfigData["stepVersificationScheme"] ?: throw StepException("For public modules, you must overtly define stepVersificationScheme")
      if (ConfigData.getAsBoolean("stepEncryptionRequired", "no")) throw StepException("You have requested encryption, but encryption cannot be applied to public modules.")
      val reversificationType = ConfigData.get("stepReversificationType", "none").lowercase()
      if ("runtime" == reversificationType) throw StepException("You have requested runtime reversification, but runtime reversification cannot be applied to a public text.")



      // Force the correct data.
      versificationScheme = VersificationSchemesSupportedByOsis2mod.canonicaliseSchemeName(versificationScheme)
      ConfigData.delete("stepVersificationScheme"); ConfigData.put("stepVersificationScheme", versificationScheme, force = true)
      ConfigData.delete("stepReversificationType"); ConfigData.put("stepReversificationType", reversificationType, force = true)
      ConfigData.delete("stepOsis2modType"); ConfigData.put("stepOsis2modType", "crosswire", force = true)
      ConfigData.put("stepSoftwareVersionRequired", "1", force = false)
      val reversificationFootnoteLevel = ConfigData["stepReversificationFootnoteLevel"]?.lowercase() ?: "basic"
      ConfigData.delete("stepReversificationFootnoteLevel"); ConfigData["stepReversificationFootnoteLevel"] = reversificationFootnoteLevel
      val reversificationFootnoteDescription = if ("none" == reversificationType) " with $reversificationFootnoteLevel footnotes" else ""


      // If NRSV(A) has been specified, assume we're good for STEP as well as open access.
      if (versificationScheme.startsWith("NRSV")) // It fits exactly.
          targetAudience += "S" // This module can also be used for STEP.
      else
      {
        val schemeEvaluation = PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.evaluateSingleScheme(versificationScheme, bibleStructureUnderConstruction)
        val schemeEvaluationSummary = schemeEvaluation.toStringShort()

        if (null != schemeEvaluationSummary)
        {
          Logger.warning(schemeEvaluationSummary)
          IssueAndInformationRecorder.setDivergencesFromSelectedVersificationScheme(schemeEvaluationSummary)
          ConfigData.addDetailsOfDerivedWork(ConfigData["stepStandardWordingForDerivedWorkWeHaveChangedVersification"]!!)
        }
      }


      // Record information for use later.
      ConfigData["stepTargetAudience"] = targetAudience



      // Record details of what's going on.
      Logger.info("Target audience is $targetAudience.")
      Logger.info("Reversification type is $reversificationType$reversificationFootnoteDescription.")
      Logger.info("osis2mod type is Crosswire, with STEP software version specified as 1.")

      return
    }



    /**************************************************************************/
    fun getNrsvVersificationScheme (bibleStructureUnderConstruction: BibleStructure): String
    {
      var versificationScheme = (ConfigData["stepVersificationScheme"] ?: "NRSV").uppercase().replace("A", "")
      if (!versificationScheme.startsWith("NRSV")) throw StepException("For STEP-internal modules, versification scheme must, if specified, be NRSV(A).")
      val requiresDc = bibleStructureUnderConstruction.hasAnyBooksDc() || ReversificationData.reversificationTargetsDc()
      if (requiresDc) versificationScheme += "A"
      return versificationScheme
    }



    /**************************************************************************/
    /* Not a public module.  As a result:

       1. The versification scheme is determined by the processing here (which
          means that no scheme should have been selected in advance).  It will
          be NRSV(A) for texts which conform to those schemes, or else will be
          a made-up name reflecting the fact that we shall be using a bespoke
          scheme.

       2. Reversification type can be anything (none / runTime / conversionTime).

       3. If reversification type is runtime or if encryption has been
          requested, osis2modType will be set to 'step'.  Otherwise it will
          be set to 'crosswire'.

       4. If osis2modType is set to 'Crosswire', then stepSoftwareLevel will
          be set to at least 1.  Otherwise it will be set to at least 2.
     */

    /**************************************************************************/
    ConfigData["stepTargetAudience"] = "S"



    /**************************************************************************/
    /* First, the versification scheme.  NRSV(A) if one of those fits and if
       the text does not contain subverses; otherwise a bespoke scheme. */

    var versificationScheme = getNrsvVersificationScheme(bibleStructureUnderConstruction) // Will be either NRSV or NRSVA.
    val schemeEvaluation = PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.evaluateSingleScheme(versificationScheme, bibleStructureUnderConstruction)
    if (schemeEvaluation.exactMatch() && !bibleStructureUnderConstruction.hasSubverses())
    {
      ConfigData.delete("stepVersificationScheme"); ConfigData.put("stepVersificationScheme", versificationScheme, force = true)
      Logger.info("Using versification scheme $versificationScheme.")
      ConfigData.delete("stepOsis2modType"); ConfigData.put("stepOsis2modType", "crosswire", force = true)
    }
    else
    {
      ConfigData.delete("stepVersificationScheme") // The absence of this setting is used elsewhere to flag the fact that we need a bespoke scheme.
      Logger.info("Using bespoke versification scheme.")
    }



    /**************************************************************************/
    /* Now reversification.  If the versification scheme is NRSV(A), then
       I take it that none is needed.  Otherwise, either conversiontime or
       runtime must have been specified, and we can go with that. */

    var reversificationType: String
    if (versificationScheme.startsWith("NRSV"))
    {
      reversificationType = "none"
      Logger.info("ReversificationType is $reversificationType")
    }
    else
    {
      reversificationType = ConfigData.get("stepReversificationType", "").lowercase()
      if ("conversiontime" != reversificationType && "runtime" != reversificationType) throw StepException("This text needs some form of reversification: you need to set stepReversificationType to conversionTime or runTime")
      ConfigData.delete("stepReversificationType"); ConfigData.put("stepReversificationType", reversificationType, force = true)

      val reversificationFootnoteLevel = ConfigData["stepReversificationFootnoteLevel"]?.lowercase() ?: "basic"
      ConfigData.delete("stepReversificationFootnoteLevel"); ConfigData["stepReversificationFootnoteLevel"] = reversificationFootnoteLevel

      Logger.info("Reversification type is $reversificationType with $reversificationFootnoteLevel footnotes.")
    }



    /**************************************************************************/
    /* We must require encryption or be using runtime reversification or else
       there's no point in doing a STEP-only module. */

    if ("none" == reversificationType && !ConfigData.getAsBoolean("stepEncryptionRequired", "no"))
      throw StepException("You have requested a STEP-only module, but no reversification is needed and you have not asked for encryption, so this could be a public module.")



    /**************************************************************************/
    /* So finally we know that we must be using STEP osis2mod, and that implies
       also that the module will run only with STEP software version 2 or
       later. */

    ConfigData.delete("stepOsis2modType"); ConfigData.put("stepOsis2modType", "step", force = true)
    var softwareVersion = ConfigData.get("stepSoftwareVersionRequired", "2").toInt()
    if (softwareVersion < 2) softwareVersion = 2
    ConfigData.put("stepSoftwareVersionRequired", softwareVersion.toString(), force = false)



    /**************************************************************************/
    Logger.info("osis2mod type is STEP, with STEP software version specified as $softwareVersion.")
    Logger.info("Target audience is $targetAudience.")



    /**************************************************************************/
    Osis_Osis2modInterface.instance().initialise()
  }
}
