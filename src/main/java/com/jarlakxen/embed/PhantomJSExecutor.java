/**
 * Copyright (C) 2012
 *   Facundo Viale <fviale@despegar.com>
 *
 * with contributions from
 * 	Facundo Viale (Jarlakxen@github)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jarlakxen.embed;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

public class PhantomJSExecutor {

	private static final Logger LOGGER = Logger.getLogger(PhantomJSExecutor.class);
	
	public static String PHANTOMJS_NATIVE_CMD = "phantomjs";
	public static String PHANTOMJS_DATA_FILE = "phantomjs/data.properties";
	
	private PhantomJSConfiguration configuration;
	private String phantomJSExecutablePath;

	public PhantomJSExecutor() {
		this(new PhantomJSConfiguration());
	}

	public PhantomJSExecutor(PhantomJSConfiguration configuration) {
		this.configuration = configuration;
		phantomJSExecutablePath = getPhantomJSExecutablePath();
	}
	
	public String execute(String sourceFilePath){
		try {
			Process process = Runtime.getRuntime().exec(this.phantomJSExecutablePath + " " + sourceFilePath);
	        process.waitFor();
	        return IOUtils.toString(process.getInputStream());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}		
	}

	private String getPhantomJSExecutablePath(){
		
		// Check if phantomjs is installed locally
		if( configuration.getCheckNativeInstallation() ){
			
			if(checkPhantomJSInstall(PHANTOMJS_NATIVE_CMD)){
				return PHANTOMJS_NATIVE_CMD;
			}
		}
		
		if(!configuration.getVersion().isDownloadSopported()){
			throw new RuntimeException("Unsopported version for downloading!");
		}
		
		// Check if phantomjs is already installed in target path
		String targetPath = configuration.getTargetInstallationFolder() + "/phantomjs";
		if(checkPhantomJSInstall(targetPath)){
			return targetPath;
		}
		
		// Try download phantomjs
		try {
		
			Properties properties = new Properties();
			properties.load(getClass().getClassLoader().getResourceAsStream(PHANTOMJS_DATA_FILE));
						
			String osHost;
			if(configuration.getHostOs().indexOf("linux") >= 0){
				osHost = "linux";
			} else if(configuration.getHostOs().indexOf("win") >= 0){
				osHost = "win";
			} else if(configuration.getHostOs().indexOf("mac") >= 0){
				osHost = "macosx";
			} else {
				throw new RuntimeException("Unsopported operation system!");
			}
			
			String name = properties.getProperty(configuration.getVersion().getDescription() + "." + osHost + ".name");
			
			String architecture = configuration.getArchitecture().indexOf("64") >= 0 ? "x86_64" : "i686";
			
			if(osHost.equals("linux")){
				name = String.format(name, architecture);
			}
			
			URL downloadPath = new URL(configuration.getDownloadUrl() +  name);
			File phantomJsCompressedFile = new File(System.getProperty("java.io.tmpdir") +"/"+ name);
			FileUtils.copyURLToFile(downloadPath, phantomJsCompressedFile);
			

			ArchiveInputStream archiveInputStream = null;
            
            if(phantomJsCompressedFile.getName().endsWith(".zip")){
            	
            	archiveInputStream = new ZipArchiveInputStream(new FileInputStream(phantomJsCompressedFile));
            	
            } else if(phantomJsCompressedFile.getName().endsWith(".bz2")){
            	
            	archiveInputStream = new TarArchiveInputStream(new BZip2CompressorInputStream(new FileInputStream(phantomJsCompressedFile)));
            	
            } else if(phantomJsCompressedFile.getName().endsWith(".gz")){
                
            	archiveInputStream = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(phantomJsCompressedFile)));
            	
            }
            
            String outputBinaryPath = null;
            
			ArchiveEntry entry;
			while ((entry = archiveInputStream.getNextEntry()) != null) {
				if(entry.getName().endsWith("/bin/phantomjs")){

					 // Create target folder
					 new File(configuration.getTargetInstallationFolder()).mkdirs();
					 
					 // Create empty binary file
					 File output = new File(configuration.getTargetInstallationFolder()+ "/phantomjs");
					 if(!output.exists()){
						 output.createNewFile();
						 output.setExecutable(true);
						 output.setReadable(true);
					 }
					 
					 // Untar the binary file
					 FileOutputStream outputBinary = new FileOutputStream(output);
					 
					 IOUtils.copy(archiveInputStream, outputBinary);
					 
					 outputBinary.close();
					 
					 outputBinaryPath = output.getAbsolutePath();
				}
			}
            
            archiveInputStream.close();

            return outputBinaryPath;
            
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Boolean checkPhantomJSInstall(String path) {
		try {
			Process process = Runtime.getRuntime().exec(path + " --version");
			process.waitFor();

			String processOutput = IOUtils.toString(process.getInputStream());

			if (PhantomJSVersion.fromValue(processOutput.substring(0, 5)) != null) {
				return true;
			}
		} catch (Exception e) {
			LOGGER.warn(e);
		}

		return false;
	}

	public PhantomJSConfiguration getConfiguration() {
		return configuration;
	}
}
