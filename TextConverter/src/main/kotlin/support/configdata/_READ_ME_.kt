package org.stepbible.textconverter.support.configdata

/******************************************************************************/
/**
* This package holds all of the stuff related to configuration and file paths.
*
*
*
*
*
* ## Overview
*
* - **[ConfigData]** handles all of the main configuration data.  It picks up
*   configuration data from the various configuration files, stores calculated
*   values which look like configuration data to the rest of the system, and
*   also puts command-line parameters into the configuration data.  The
*   configuration files are discussed in more details below.
*
* - **[ConfigDataExternalFileInterface]** contains code which lets you pick up
*   configuration information from external sources.  At present I support only
*   the metadata.xml files which are supplied by DBL.  The processing permits
*   you to pick up configuration data direct from these files, rather than
*   having to transcribe it manually into our own format.
*
* - **[StandardFileLocations]** gives you all of the file locations (eg where
*   to find the Metadata folder, the OSIS folder, etc), along with things like
*   patterns by which particular types of files can be recognised.
*
*
*
*
*
* ## Configuration files
*
* You need to create at least one configuration file of your own to give the
* processing some basic information.  That file can load standard files which
* supply defaults for most configuration parameters, so you need supply your
* own values only where no defaults are available or where the default is not
* what you require.
*
* All of this is discussed in huge detail in the various default configuration
* files which are stored in the Resources section of this JAR file.  I strongly
* recommend starting off with the _readMeFirst_.txt file there, which gives
* a detailed description of the way configuration data works, and then explains
* where to look in the other configuration files for more information.
*
* The system does not require configuration data to be assigned to different
* files in any particular manner, but I have found it convenient to split
* things up so that each logically-related group of configuration data goes
* into its own file, and related files go into their own folder.  Most of
* these files give more information about how to use the particular kinds
* of configuration information they contain.
*
*/

private class _READ_ME_
