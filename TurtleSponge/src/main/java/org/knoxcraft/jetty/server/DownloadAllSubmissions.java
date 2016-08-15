package org.knoxcraft.jetty.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.knoxcraft.database.DataAccess;
import org.knoxcraft.database.Database;
import org.knoxcraft.database.exceptions.DatabaseReadException;
import org.knoxcraft.database.tables.KCTScriptAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DownloadAllSubmissions extends HttpServlet
{
    private static final long serialVersionUID = 1L;
    private Logger logger=LoggerFactory.getLogger(DownloadAllSubmissions.class);
    
    public DownloadAllSubmissions() {
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
    throws ServletException, IOException
    {
        // FIXME: translate to Sponge
        try {
            OutputStream out=response.getOutputStream();
            ZipOutputStream zip=new ZipOutputStream(out);
            
            response.setContentType("application/zip");     
            //response.setContentLength((int)f.length());
            response.addHeader("Content-Encoding", "zip"); 
            response.addHeader("Content-Disposition","attachment;filename=\"submissions.zip\"");    
            
            KCTScriptAccess data=new KCTScriptAccess();
            List<DataAccess> results=new LinkedList<DataAccess>();
            Map<String,Object> filters=new HashMap<String,Object>();
            Database.get().loadAll(data, results, filters);
            
            Map<String,KCTScriptAccess> mostRecentScripts=KCTScriptAccess.getMostRecentScripts();
            
            for (KCTScriptAccess scriptAccess : mostRecentScripts.values()) {
                String extension=scriptAccess.language;
                if (extension.equals("python")) {
                    extension="py";
                }
                ZipEntry entry=new ZipEntry(
                        String.format("submissions/%s/%s.%s", scriptAccess.playerName, scriptAccess.scriptName, extension));
                zip.putNextEntry(entry);
                IOUtils.copy(new ByteArrayInputStream(scriptAccess.source.getBytes()), zip);
                zip.closeEntry();
            }
            zip.flush();
            zip.close();
            
        } catch (DatabaseReadException e) {
            logger.error("Cannot read DB", e);
        } catch (IOException e){
            logger.error("Zip error", e);
        }
    }
}
