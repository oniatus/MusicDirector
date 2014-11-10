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

import java.util.Collection;
import java.util.PriorityQueue;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.AssetManager;
import org.terasology.asset.Assets;
import org.terasology.audio.AudioEndListener;
import org.terasology.audio.AudioManager;
import org.terasology.audio.StreamingSound;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.console.Command;
import org.terasology.registry.In;
import org.terasology.registry.Share;
import org.terasology.world.time.WorldTimeEvent;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Play different music assets through triggers that are
 * provided by (auto-created) entities with
 * {@link MusicTrigger} components.
 * @author Martin Steiger
 */
@RegisterSystem
@Share(MusicDirector.class)
public class MusicDirectorImpl extends BaseComponentSystem implements MusicDirector {

    private static final Logger logger = LoggerFactory.getLogger(MusicDirectorImpl.class);

    private final Collection<MusicTrigger> triggers = Sets.newLinkedHashSet();

    private final Queue<MusicTrigger> playList = new PriorityQueue<MusicTrigger>(new TriggerComparator());

    private final Queue<String> history = Lists.newLinkedList();

    @In
    private AudioManager audioManager;

    @In
    private AssetManager assetManager;

    private StreamingSound current;

    @ReceiveEvent
    public void onTimeEvent(WorldTimeEvent event, EntityRef worldEntity) {

        checkTriggers();
    }

    @Override
    public void register(MusicTrigger trigger) {
        logger.info("Registered music asset {}", trigger.getAssetUri());
        triggers.add(trigger);
        checkTriggers();
    }

    private void checkTriggers() {

        for (MusicTrigger trigger : triggers) {
            if (trigger.isTriggered()) {
                if (!playList.contains(trigger)) {
                    logger.info("Music trigger {} activated", trigger);
                    playList.add(trigger);
                }
            } else {
                playList.remove(trigger);
            }
        }

        if (current == null && !playList.isEmpty()) {
            MusicTrigger best = playList.peek();

            String uri = best.getAssetUri();
            current = Assets.getMusic(uri);

            if (current != null) {
                logger.info("Starting to play '{}'", uri);
                audioManager.playMusic(current, new AudioEndListener() {

                    @Override
                    public void onAudioEnd() {
                        logger.info("Song ended");
                        playList.poll();    // remove head
                        history.add(uri);
                        current = null;
                    }
                });
            } else {
                logger.warn("Asset {} could not be retrieved", uri);
            }
        }
    }

    @Command(shortDescription = "Show current playlist")
    public String showPlaylist() {
        StringBuilder sb = new StringBuilder();

        sb.append("In the queue: ");
        sb.append(playList.toString());
        sb.append("\n");
        sb.append(current != null ? "Currently playing '" + current.getURI() + "'" : "Not playing");

        return sb.toString();
    }
}