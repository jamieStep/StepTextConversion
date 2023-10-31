package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils

/******************************************************************************/
/**
 * Base class for main processors.
 *
 * @author ARA "Jamie" Jamieson
 */

abstract class TextConverterProcessorBase
{
    /****************************************************************************/
    /**
     * Returns a banner to reflect progress.
     *
     * @return Banner
     */

    abstract fun banner (): String


    /****************************************************************************/
    /**
     * Returns details of any command-line parameters this processor requires or permits.
     *
     * @param commandLineProcessor Command line processor.
     */

    abstract fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)



    /******************************************************************************************************************/
    /**
     * The present class (or rather its derivative) forms part of a processing chain, and the 'pre' methods of all
     * elements of that chain are called consecutively <i>before</i> calling the 'process' methods.  Typically the 'pre'
     * method should ensure that any folders needed by the processor are present, and that previous copies of any output
     * files it generates have been deleted.
     *
     * @return True if processing should continue.
     */

    abstract fun pre (): Boolean



    /******************************************************************************************************************/
    /**
     * The processing method.
     *
     * @return True if ok to continue to the next stage of processing.
     */

    abstract fun process (): Boolean



    /******************************************************************************************************************/
    /**
     * Returns an indication of whether the class derived from this one is to do its stuff.
     *
     * @return True if the class should be run.
     */

    abstract fun runMe (): Boolean



    /******************************************************************************************************************/
    /**
     * Creates all of the folders listed in the argument.  If necessary, the parent folders of the given folder are
     * created too.
     *
     * @param foldersToBeCreated What it says on the tin.
     */

    protected fun createFolders (foldersToBeCreated: List<String>)
    {
        foldersToBeCreated.forEach {
            if (!StepFileUtils.fileExists(it))
                StepFileUtils.createFolderStructure(it)
        }
    }


    /******************************************************************************************************************/
    /**
     * Deletes each of a list of files.  Each file is represented by a pair of strings.  The first of these is a path,
     * and the second is a pattern-match file name, or null.
     *
     * If the second element is null, the first element is a full path for a single file to be deleted.
     *
     * If the second element is non-null, the first element is a path to the containing folder, and the second is a
     * DOS-style pattern match for the names of the files to be deleted.
     *
     * @param filesToBeDeleted Details of files to be deleted.
     */

    protected fun deleteFiles (filesToBeDeleted: List<Pair<String, String?>>)
    {
      filesToBeDeleted.forEach { deleteFile(it) }
    }


    /**************************************************************************************************************/
    protected fun deleteFile (fileDetails: Pair<String, String?>)
    {
        var (path, pattern) = fileDetails
        if (null == pattern)
        {
            pattern = StepFileUtils.getFileName(path)
            path = StepFileUtils.getParentFolderName(path)
        }

        try { StepFileUtils.getMatchingThingsFromFolder(path, ("\\Q$pattern\\E").toRegex(), "DF").forEach{ StepFileUtils.deleteFileOrFolder(it.toString()) } } catch (_: Exception) {}
    }


    /**************************************************************************************************************/
    protected fun deleteFolder (folderPath: String)
    {
      StepFileUtils.deleteFolder(folderPath)
    }
}
