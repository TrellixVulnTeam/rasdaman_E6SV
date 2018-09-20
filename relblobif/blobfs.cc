/*
* This file is part of rasdaman community.
*
* Rasdaman community is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Rasdaman community is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with rasdaman community.  If not, see <http://www.gnu.org/licenses/>.
*
* Copyright 2003 - 2015 Peter Baumann / rasdaman GmbH.
*
* For more information please see <http://www.rasdaman.org>
* or contact Peter Baumann via <baumann@rasdaman.com>.
*/
/*************************************************************
 *
 * PURPOSE:
 * The interface used by the file storage modules.
 *
 *
 * COMMENTS:
 *
 ************************************************************/

#include "config.h"
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <limits.h>
#include <string.h>
#include <stdio.h>
#include <logging.hh>
#include "blobfscommon.hh"
#include "blobtile.hh"
#include "dirwrapper.hh"
#include "blobfs.hh"
#include "blobfstransactionlock.hh"

using namespace std;
using namespace blobfs;

#ifndef FILESTORAGE_TILES_SUBDIR
#define FILESTORAGE_TILES_SUBDIR "TILES"
#endif

#ifndef FILESTORAGE_TRANSACTIONS_SUBDIR
#define FILESTORAGE_TRANSACTIONS_SUBDIR "TRANSACTIONS"
#endif

extern char globalConnectId[PATH_MAX];

BlobFS& BlobFS::getInstance()
{
    static BlobFS instance;
    return instance;
}

BlobFS::BlobFS()
    : config(BlobFS::getFileStorageRootPath(), string(""), string("")),
      insertTransaction(NULL), updateTransaction(NULL), removeTransaction(NULL), selectTransaction(NULL)
{
    init();
}

BlobFS::BlobFS(const string& rasdataPathParam)
    : config(DirWrapper::convertToCanonicalPath(rasdataPathParam), string(""), string("")),
      insertTransaction(NULL), updateTransaction(NULL), removeTransaction(NULL), selectTransaction(NULL)
{
    init();
}

void BlobFS::init()
{
    LDEBUG << "initializing file storage on directory " << config.rootPath;
    if (config.rootPath.empty())
    {
        LERROR << "blob file storage data directory has not been set; "
               << "please set the -connect value in rasmgr.conf.";
        throw r_Error(static_cast<unsigned int>(FILEDATADIR_NOTFOUND));
    }

    validateFileStorageRootPath();
    config.tilesPath = getTilesRootPath();
    DirWrapper::createDirectory(config.tilesPath);
    config.transactionsPath = getTransactionsRootPath();
    DirWrapper::createDirectory(config.transactionsPath);

    insertTransaction = new BlobFSInsertTransaction(config);
    updateTransaction = new BlobFSUpdateTransaction(config);
    removeTransaction = new BlobFSRemoveTransaction(config);
    selectTransaction = new BlobFSSelectTransaction(config);

    finalizeUncompletedTransactions();

    LDEBUG << "initialized blob file storage handler with root data directory " << config.tilesPath;
}

void BlobFS::validateFileStorageRootPath()
{
    struct stat status;
    if (stat(config.rootPath.c_str(), &status) == -1)
    {
        generateError("blob file storage data directory not found", config.rootPath, FILEDATADIR_NOTFOUND);
    }
    if (!S_ISDIR(status.st_mode))
    {
        generateError("path to blob file storage is not a directory", config.rootPath, FILEDATADIR_NOTFOUND);
    }
    if (access(config.rootPath.c_str(), W_OK | X_OK) == -1)
    {
        generateError("blob file storage data directory is not writable", config.rootPath, FILEDATADIR_NOTWRITABLE);
    }
}

void BlobFS::insert(BlobData& blob)
{
    insertTransaction->add(blob);
}

void BlobFS::update(BlobData& blob)
{
    updateTransaction->add(blob);
}

void BlobFS::select(BlobData& blob)
{
    selectTransaction->add(blob);
}

void BlobFS::remove(BlobData& blob)
{
    removeTransaction->add(blob);
}

const string BlobFS::getTilesRootPath()
{
    const string ret = config.rootPath + FILESTORAGE_TILES_SUBDIR + '/';
    return ret;
}

const string BlobFS::getTransactionsRootPath()
{
    const string ret = config.rootPath + FILESTORAGE_TRANSACTIONS_SUBDIR + '/';
    return ret;
}

const string BlobFS::getFileStorageRootPath()
{
    auto rootPath = DirWrapper::getBasename(globalConnectId);
    if (rootPath.empty())
    {
        LERROR << "The file storage root path must be an absolute path.";
        throw r_Error(static_cast<unsigned int>(FILEDATADIR_NOTABSOLUTE));
    }

    char* deprecatedPath = getenv("RASDATA");
    if (deprecatedPath != NULL && strcmp(deprecatedPath, "") != 0 && strcmp(deprecatedPath, rootPath.c_str()) != 0)
    {
        LWARNING << "The filestorage root path was inferred to be '" << rootPath
                 << "' according to the -connect string specified in etc/rasmgr.conf; "
                 << "This is, however, different from the deprecated $RASDATA env variable which "
                 << "points to " << deprecatedPath << ". $RASDATA will be ignored, "
                 << "if necessary please migrate any data from this location to "
                 << "the correct filestorage root path and unset it in the environment to "
                 << "avoid this warning in future.";
    }
    return DirWrapper::convertToCanonicalPath(rootPath);
}

void BlobFS::generateError(const char* message, const string& path, int errorCode)
{
    LERROR << "Error: " << message << " - " << path;
    LERROR << "Reason: " << strerror(errno);
    throw r_Error(static_cast<unsigned int>(errorCode));
}

void BlobFS::preRasbaseCommit()
{
    insertTransaction->preRasbaseCommit();
    updateTransaction->preRasbaseCommit();
    selectTransaction->preRasbaseCommit();
    removeTransaction->preRasbaseCommit();
}

void BlobFS::postRasbaseCommit()
{
    insertTransaction->postRasbaseCommit();
    updateTransaction->postRasbaseCommit();
    selectTransaction->postRasbaseCommit();
    removeTransaction->postRasbaseCommit();
}

void BlobFS::postRasbaseAbort()
{
    insertTransaction->postRasbaseAbort();
    updateTransaction->postRasbaseAbort();
    selectTransaction->postRasbaseAbort();
    removeTransaction->postRasbaseAbort();
}

void BlobFS::finalizeUncompletedTransactions()
{
    DirEntryIterator subdirIterator(config.transactionsPath);
    if (subdirIterator.open())
    {
        for (string subdir = subdirIterator.next(); !subdirIterator.done(); subdir = subdirIterator.next())
        {
            if (subdir.empty())
            {
                continue;
            }
            LockFile checkTransactionLock(DirWrapper::convertFromCanonicalPath(subdir) + ".lock");
            if (checkTransactionLock.lock())
            {
                BlobFSTransactionLock transactionLock(subdir, true);
                if (!transactionLock.lockedForTransaction())
                {
                    transactionLock.clearTransactionLock();
                    BlobFSTransaction* transaction = BlobFSTransaction::getBlobFSTransaction(subdir, config);
                    if (transaction != NULL)
                    {
                        NNLDEBUG << "transaction in invalid state discovered, recovering...";
                        transaction->finalizeUncompleted();
                        delete transaction;
                        transaction = NULL;
                        BLDEBUG << "ok.";
                    }
                }
                checkTransactionLock.unlock();
            }
        }
        subdirIterator.close();
    }
}

BlobFS::~BlobFS()
{
    if (insertTransaction != NULL)
    {
        delete insertTransaction;
        insertTransaction = NULL;
    }
    if (updateTransaction != NULL)
    {
        delete updateTransaction;
        updateTransaction = NULL;
    }
    if (removeTransaction != NULL)
    {
        delete removeTransaction;
        removeTransaction = NULL;
    }
    if (selectTransaction != NULL)
    {
        delete selectTransaction;
        selectTransaction = NULL;
    }
}
