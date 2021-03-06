/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.musicdirector;

import java.util.Comparator;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.utilities.Assets;
import org.terasology.audio.AudioEndListener;
import org.terasology.audio.AudioManager;
import org.terasology.audio.StreamingSound;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.registry.In;
import org.terasology.registry.Share;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * Play different music assets that are enqueued
 * according to their priority.
 */
@RegisterSystem
@Share(MusicDirector.class)
public class MusicDirectorImpl extends BaseComponentSystem implements MusicDirector {

    private static final Logger logger = LoggerFactory.getLogger(MusicDirectorImpl.class);

    private static final TriggerComparator COMP = new TriggerComparator();

    private final Queue<PlaylistEntry> playList = new PriorityQueue<>(COMP);
    private final Queue<PlaylistEntry> history = Lists.newLinkedList();

    @In
    private AudioManager audioManager;

    private PlaylistEntry currentEntry;
    private StreamingSound currentSound;

    @Override
    public void enqueue(String assetUri, MusicPriority priority) {

        PlaylistEntry prev = find(assetUri);

        PlaylistEntry entry = new PlaylistEntry(assetUri, priority);

        if (prev != null) {
            if (!prev.equals(entry)) {
                playList.remove(prev);
                playList.add(entry);
                logger.info("Updated {} with {}", prev, entry);
            } else {
                // already exists
                return;
            }
        } else {
            logger.info("Enqueued {}", assetUri);
            playList.add(entry);
        }

        checkTriggers();
    }

    @Override
    public void dequeue(String assetUri) {
        PlaylistEntry entry = find(assetUri);
        if (entry != null) {
            playList.remove(entry);
            logger.info("Removed {}", entry.getAssetUri());
        }
    }

    private PlaylistEntry find(String assetUri) {
        for (PlaylistEntry entry : playList) {
            if (assetUri.equalsIgnoreCase(entry.getAssetUri())) {
                return entry;
            }
        }

        return null;
    }

    private void checkTriggers() {

        if (playList.isEmpty()) {
            return;
        }

        PlaylistEntry nextEntry = playList.peek();

        if (currentEntry == null || COMP.compare(nextEntry,  currentEntry) > 0) {

            String uri = nextEntry.getAssetUri();
            Optional<StreamingSound> optSound = Assets.getMusic(uri);

            if (optSound.isPresent()) {
                if (currentSound != null) {
//                    currentSound.stop();
                }

                currentSound = optSound.get();
                currentEntry = nextEntry;

                logger.info("Starting to play '{}'", uri);
                audioManager.playMusic(currentSound, new AudioEndListener() {

                    @Override
                    public void onAudioEnd() {
                        logger.info("Song '{}' ended", currentEntry.getAssetUri());
                        playList.remove(currentEntry);    // remove head
                        history.add(currentEntry);
                        currentEntry = null;
                        currentSound = null;
                        checkTriggers();
                    }
                });
            } else {
                logger.warn("Asset {} could not be retrieved", uri);
                playList.remove();
                checkTriggers();
            }
        }
    }

    @Command(shortDescription = "Show current playlist")
    public String showPlaylist() {
        StringBuilder sb = new StringBuilder();

        sb.append("In the queue:\n");
        Joiner.on('\n').appendTo(sb, playList);
        sb.append("\n");
        sb.append(currentEntry != null ? "Currently playing '" + currentEntry.getAssetUri() + "'" : "Not playing");

        return sb.toString();
    }

    private static class TriggerComparator implements Comparator<PlaylistEntry> {

        @Override
        public int compare(PlaylistEntry o1, PlaylistEntry o2) {
            MusicPriority prio1 = o1.getPriority();
            MusicPriority prio2 = o2.getPriority();
            return Integer.compare(prio1.getValue(), prio2.getValue());
        }

    }
}
