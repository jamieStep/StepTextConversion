package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.protocolagnosticutils.PA_ElementArchiver
import org.stepbible.textconverter.protocolagnosticutils.reversification.PA_ReversificationUtilities

/****************************************************************************/
/**
 * Updates any cross-references which may have been affected by
 * reversification.
 *
 * Conversion-time reversification is conceptually fairly easy, in that
 * we may physically renumber verses, and if we do so, then anything which
 * pointed to an 'old' verse reference now needs to point to the new one.
 * This is not to say that the actual functionality to address this is easy --
 * each cross-reference normally consists of two forms of the reference, an
 * 'internal' one which is constrained to follow the OSIS syntax rules, and an
 * 'external' (user-visible) one which will normally be in vernacular format,
 * and may be embedded within additional explanatory text.  It is not difficult
 * to update the internal form appropriately, but it may well be nigh on
 * impossible to modify the external form in line with the change.  However, it
 * has been suggested to me that in fact the external text should not be changed
 * at all, because its original content is meaningful to the user where its
 * revised form will not be.
 *
 * Runtime reversification is much easier from the point of view of the
 * implementation, because it entails no restructuring, and there is therefore
 * nothing for me to do.  However, it does give rise to some conceptual
 * problems ...
 *
 * As I say we do not restructure the text, and when it is displayed standalone
 * in STEPBible, it therefore follows the original structure as supplied by the
 * translators -- given which, it does indeed make sense to retain the cross-
 * references as they were supplied to us.
 *
 * When displayed as part of an interlinear display, however, there is an issue,
 * because verses may be renumbered on the fly.  In this case, some cross-
 * references may no longer be valid, because they will point to the *original*
 * references at a time when the verses are being displayed with *revised*
 * references.  Unfortunately so far as I can see there is nothing we can do
 * about this.
 *
 * @author ARA "Jamie" Jamieson
 */

object Osis_CrossReferenceUpdater: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                  Public                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Updates cross-references where appropriate.
  *
  * @param archive Archive which contains the cross-references.
  */

  fun process (dataCollection: X_DataCollection, archive: PA_ElementArchiver)
  {
    val versificationMappings = Osis_AudienceAndCopyrightSpecificProcessingHandler.getReversificationHandler()?.getCrossReferenceMappings() ?: return
    Rpt.report(level = 1, "Updating cross references to reflect versification changes.")
    archive.modifyReferences(dataCollection, versificationMappings, "bcvs")
  }
}
