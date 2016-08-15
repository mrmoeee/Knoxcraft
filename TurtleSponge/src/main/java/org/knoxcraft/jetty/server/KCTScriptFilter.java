package org.knoxcraft.jetty.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.knoxcraft.database.DataAccess;
import org.knoxcraft.database.Database;
import org.knoxcraft.database.exceptions.DatabaseReadException;
import org.knoxcraft.database.tables.KCTScriptAccess;
import org.knoxcraft.turtle3d.KCTScript;
import org.knoxcraft.turtle3d.TurtleCompiler;
import org.knoxcraft.turtle3d.TurtleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@WebFilter("/RequestLoggingFilter")
public class KCTScriptFilter extends DefaultFilter
{
    private Logger log=LoggerFactory.getLogger(KCTScriptFilter.class);

    public KCTScriptFilter() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException 
    {
        HttpServletRequest req = (HttpServletRequest) request;

        KCTScriptAccess data=new KCTScriptAccess();
        List<DataAccess> results=new LinkedList<DataAccess>();
        Map<String,KCTScriptAccess> mostRecentScripts=new HashMap<String,KCTScriptAccess>();

        try {
            Map<String,Object> filters=new HashMap<String,Object>();
            Database.get().loadAll(data, results, filters);
            for (DataAccess d : results) {
                KCTScriptAccess scriptAccess=(KCTScriptAccess)d;
                // Figure out the most recent script for each player-scriptname combo
                String key=scriptAccess.playerName+"-"+scriptAccess.scriptName;
                if (!mostRecentScripts.containsKey(key)) {
                    mostRecentScripts.put(key, scriptAccess);
                } else {
                    if (scriptAccess.timestamp > mostRecentScripts.get(key).timestamp) {
                        mostRecentScripts.put(key,scriptAccess);
                    }
                }
                log.trace(String.format("from DB: player %s has script %s at time %d%n", 
                        scriptAccess.playerName, scriptAccess.scriptName, scriptAccess.timestamp));
            }
            
            Map<String,KCTScript> allScripts=new HashMap<>();
            
            TurtleCompiler turtleCompiler=new TurtleCompiler();
            for (Entry<String,KCTScriptAccess> entry : mostRecentScripts.entrySet()) {
                try {
                    KCTScriptAccess scriptAccess=entry.getValue();
                    KCTScript script=turtleCompiler.parseFromJson(scriptAccess.json);
                    script.setLanguage(scriptAccess.language);
                    script.setScriptName(scriptAccess.scriptName);
                    script.setSourceCode(scriptAccess.source);
                    script.setPlayerName(scriptAccess.playerName);

                    allScripts.put(entry.getKey(), script);
                    log.info(String.format("Loaded script %s for player %s", 
                            scriptAccess.scriptName, scriptAccess.playerName));
                } catch (TurtleException e){
                    log.error("Internal Server error", e);
                }
            }
            req.setAttribute("scripts", allScripts);
        } catch (DatabaseReadException e) {
            log.error("cannot read DB", e);
        }
        chain.doFilter(request, response);
    }

}
