package com.opersys.processexplorer.tasks;

import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.util.Log;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.util.Enumeration;

/**
 * Date: 27/03/14
 * Time: 3:30 PM
 */
public abstract class AssetExtractTask extends AsyncTask<AssetExtractTaskParams, Integer, Void> {

    private static final String TAG = "AssetExtractTask";

    public static boolean isExtractRequired(AssetExtractTaskParams params) {
        AssetManager assetManager;
        String assetMd5sumPath, assetMd5sum, exMd5sum;
        File exMd5sumFile;
        BufferedReader assetMd5in, exMd5in;

        assetManager = params.assetManager;
        assetMd5sumPath = params.assetMd5sumPath;
        exMd5sumFile = new File(params.extractPath + File.separator + "md5sum");

        try {
            assetMd5in = new BufferedReader(new InputStreamReader(assetManager.open(assetMd5sumPath)));
            exMd5in = new BufferedReader(new FileReader(exMd5sumFile));

            assetMd5sum = assetMd5in.readLine();
            exMd5sum = exMd5in.readLine();

            assetMd5in.close();
            exMd5in.close();

        } catch (IOException e) {
            Log.w(TAG, "Error trying to determine if data extraction is required", e);

            return true;
        }

        return !(assetMd5sum.trim().equals(exMd5sum.trim()));
    }

    protected void chmod(String mode, String target) {
        Process chmodProcess;
        String[] chmodArr = { "/system/bin/chmod", mode, target };

        try {
            chmodProcess = Runtime.getRuntime().exec(chmodArr);
            chmodProcess.waitFor();

        } catch (IOException e) {
            Log.e(TAG, "Failed to chmod " + target + " to " + mode);

        } catch (InterruptedException e) {
            Log.w(TAG, "Process.waitFor() interrupted");
        }
    }

    @Override
    protected Void doInBackground(AssetExtractTaskParams... params) {
        int totalSize = 0, partialSize = 0;
        File zipFile;
        ZipFile zf;
        InputStream is;
        OutputStream os;
        String assetPath = params[0].assetPath;
        File extractPath = params[0].extractPath;
        AssetManager assetManager = params[0].assetManager;
        ZipArchiveEntry zentry = null;

        zipFile = new File(extractPath + File.separator + assetPath);

        try {
            is = assetManager.open(assetPath);
            os = new FileOutputStream(zipFile);

            IOUtils.copy(is, os);

            zf = new ZipFile(zipFile);

            for (Enumeration<ZipArchiveEntry> ez = zf.getEntries(); ez.hasMoreElements();) {
                zentry = ez.nextElement();
                totalSize += zentry.getSize();
            }

            Log.d(TAG, "Total size of entries is: " + totalSize);

            for (Enumeration<ZipArchiveEntry> ez = zf.getEntries(); ez.hasMoreElements();) {
                zentry = ez.nextElement();

                final File outputTarget = new File(extractPath, zentry.getName());

                if (zentry.isDirectory()) {
                    if (!outputTarget.exists()) {
                        if (!outputTarget.mkdirs()) {
                            String s = String.format("Couldn't create directory %s.", outputTarget.getAbsolutePath());
                            throw new IllegalStateException(s);
                        }
                    }
                } else {
                    final File parentTarget = new File(outputTarget.getParent());

                    // Make the parent directory if it doesn't exists.
                    if (!parentTarget.exists())
                    {
                        if (!parentTarget.mkdirs()) {
                            String s = String.format("Couldn't create directory %s.", parentTarget.toString());
                            throw new IllegalStateException(s);
                        }
                    }

                    final OutputStream outputFileStream = new FileOutputStream(outputTarget);
                    IOUtils.copy(zf.getInputStream(zentry), outputFileStream);
                    outputFileStream.close();

                    Log.d(TAG, "Done " + outputTarget.toString() + " (" + zentry.getSize() + ")");

                    partialSize += zentry.getSize();
                    onProgressUpdate((int)(((float)partialSize / (float)totalSize) * 100.0));
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "Asset decompression error", ex);
        } finally {
            zipFile.delete();
        }

        // FIXME: This is a hack. I need to make up something else eventually.
        chmod("0755", extractPath + File.separator + "node");

        // Copy the md5sum of the asset file to the disk.
        try {
            is = assetManager.open(params[0].assetMd5sumPath);
            os = new FileOutputStream(new File(extractPath + File.separator + "md5sum"));

            IOUtils.copy(is, os);

            is.close();
            os.close();

        } catch (IOException e) {
            Log.e(TAG, "Exception with extracting the asset md5sum file", e);
        }

        // No need to return anything since this is a task executed for side effects.
        return null;
    }
}