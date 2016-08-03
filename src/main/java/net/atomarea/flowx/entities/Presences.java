package net.atomarea.flowx.entities;

import android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.atomarea.flowx.xml.Element;

public class Presences {
	private final Hashtable<String, Presence> presences = new Hashtable<>();

	public Hashtable<String, Presence> getPresences() {
		return this.presences;
	}

	public void updatePresence(String resource, Presence presence) {
		synchronized (this.presences) {
			this.presences.put(resource, presence);
		}
	}

	public void removePresence(String resource) {
		synchronized (this.presences) {
			this.presences.remove(resource);
		}
	}

	public void clearPresences() {
		synchronized (this.presences) {
			this.presences.clear();
		}
	}

	public Presence.Status getShownStatus() {
		Presence.Status status = Presence.Status.OFFLINE;
		synchronized (this.presences) {
			for(Presence p : presences.values()) {
				if (p.getStatus() == Presence.Status.DND) {
					return p.getStatus();
				} else if (p.getStatus().compareTo(status) < 0){
					status = p.getStatus();
				}
			}
		}
		return status;
	}

	public int size() {
		synchronized (this.presences) {
			return presences.size();
		}
	}

	public String[] toResourceArray() {
		synchronized (this.presences) {
			final String[] presencesArray = new String[presences.size()];
			presences.keySet().toArray(presencesArray);
			return presencesArray;
		}
	}

	public List<PresenceTemplate> asTemplates() {
		synchronized (this.presences) {
			ArrayList<PresenceTemplate> templates = new ArrayList<>(presences.size());
			for(Presence p : presences.values()) {
				if (p.getMessage() != null && !p.getMessage().trim().isEmpty()) {
					templates.add(new PresenceTemplate(p.getStatus(), p.getMessage()));
				}
			}
			return templates;
		}
	}

	public boolean has(String presence) {
		synchronized (this.presences) {
			return presences.containsKey(presence);
		}
	}

	public List<String> getStatusMessages() {
		ArrayList<String> messages = new ArrayList<>();
		synchronized (this.presences) {
			for(Presence presence : this.presences.values()) {
				String message = presence.getMessage() == null ? null : presence.getMessage().trim();
				if (message != null && !message.isEmpty() && !messages.contains(message)) {
					messages.add(message);
				}
			}
		}
		return messages;
	}

	public boolean allOrNonSupport(String namespace) {
		synchronized (this.presences) {
			for(Presence presence : this.presences.values()) {
				ServiceDiscoveryResult disco = presence.getServiceDiscoveryResult();
				if (disco == null || !disco.getFeatures().contains(namespace)) {
					return false;
				}
			}
		}
		return true;
	}

	public Pair<Map<String, String>,Map<String,String>> toTypeAndNameMap() {
		Map<String,String> typeMap = new HashMap<>();
		Map<String,String> nameMap = new HashMap<>();
		synchronized (this.presences) {
			for(Map.Entry<String,Presence> presenceEntry : this.presences.entrySet()) {
				String resource = presenceEntry.getKey();
				Presence presence = presenceEntry.getValue();
				ServiceDiscoveryResult serviceDiscoveryResult = presence == null ? null : presence.getServiceDiscoveryResult();
				if (serviceDiscoveryResult != null && serviceDiscoveryResult.getIdentities().size() > 0) {
					ServiceDiscoveryResult.Identity identity = serviceDiscoveryResult.getIdentities().get(0);
					String type = identity.getType();
					String name = identity.getName();
					if (type != null) {
						typeMap.put(resource,type);
					}
					if (name != null) {
						nameMap.put(resource, name);
					}
				}
			}
		}
		return new Pair(typeMap,nameMap);
	}
}
