/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.layer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.Configuration;
import org.geowebcache.grid.GridSetBroker;

public class TileLayerDispatcher {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayerDispatcher.class);

    private HashMap<String, TileLayer> layers = null;

    private List<Configuration> configs = null;

    private GridSetBroker gridSetBroker;
    
    public TileLayerDispatcher(GridSetBroker gridSetBroker, List<Configuration> configs) {
        this.gridSetBroker = gridSetBroker;
        
        this.configs = configs;
        
        layers = initialize();
    }

    public TileLayer getTileLayer(String layerIdent)
            throws GeoWebCacheException {

        HashMap<String, TileLayer> tmpLayers = null;
        synchronized(this) {
            tmpLayers = this.getLayers();
        }
        
        TileLayer layer = tmpLayers.get(layerIdent);
        if (layer == null) {
            throw new GeoWebCacheException("Thread " + Thread.currentThread().getId() + " Unknown layer " + layerIdent
                    + ". Check the logfiles,"
                    + " it may not have loaded properly.");
        }

        return layer;
    }

    /***
     * Reinitialization is tricky, because we can't really just lock all the
     * layers, because this would cause people to queue on something that we may
     * not want to exist post reinit.
     * 
     * So we'll just set the current layer set free, ready for garbage
     * collection, and generate a new one.
     * 
     * @throws GeoWebCacheException
     */
    public synchronized void reInit() throws GeoWebCacheException {
        synchronized (this) {
            this.layers = null;
            this.layers = initialize();
        }
    }
    
    /**
     * Returns a list of all the layers. The consumer may still have to
     * initialize each layer!
     * 
     * @return
     */
    public HashMap<String, TileLayer> getLayers() {
        return this.layers;
    }

    private HashMap<String, TileLayer> initialize() {
        log.debug("Thread initLayers(), initializing");

        HashMap<String, TileLayer> newLayers = new HashMap<String, TileLayer>();

        Iterator<Configuration> configIter = configs.iterator();
        
        while (configIter.hasNext()) {
            List<TileLayer> configLayers = null;

            Configuration config = configIter.next();

            String configIdent = null;
            try { 
                configIdent = config.getIdentifier();
            } catch (GeoWebCacheException gwce) {
                log.error(gwce.getMessage());
            }
            
            if (configIdent != null) {
                try {
                    // This is used by reload as well
                    configLayers = config.getTileLayers(true);
                } catch (GeoWebCacheException gwce) {
                    log.error(gwce.getMessage());
                    log.error("Failed to add layers from " + configIdent);
                }

                log.info("Adding layers from " + configIdent);
                if (configLayers != null && configLayers.size() > 0) {
                    Iterator<TileLayer> iter = configLayers.iterator();
                    
                    while (iter.hasNext()) {
                        TileLayer layer = iter.next();
                        
                        log.info("Adding: " + layer.getName());
                        
                        layer.initialize(gridSetBroker);
                        
                        add(layer, newLayers);
                    }
                } else {
                    log.error("Configuration " + configIdent
                            + " contained no layers.");
                }
            }
        }

        return newLayers;
    }
    
    public synchronized void update(TileLayer layer) {
        TileLayer oldLayer = layers.get(layer.getName());
        oldLayer.acquireLayerLock();
        layers.remove(layer.getName());
        oldLayer.releaseLayerLock();
        layers.put(layer.getName(), layer);
    }
    
    public synchronized void remove(String layerName) {
        TileLayer layer = layers.get(layerName);
        layer.acquireLayerLock();
        layers.remove(layerName);
        layer.releaseLayerLock();
    }
    
    public synchronized void add(TileLayer layer) {        
        add(layer, this.layers);
    }
    
    private synchronized void add(TileLayer layer, HashMap<String, TileLayer> layerMap) {        
        if(layerMap.containsKey(layer.getName())) {
            try {
                layerMap.get(layer.getName()).mergeWith(layer);
            } catch (GeoWebCacheException gwce) {
                log.error(gwce.getMessage());
            }
        } else {
            layerMap.put(layer.getName(), layer);
        }
    }
}
