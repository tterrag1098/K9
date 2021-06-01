package com.tterrag.k9.commands;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.common.util.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Command
public class CommandMinecraftAge extends CommandBase {
    
    private static final Argument<String> VERSION = new WordArgument("version", "The Minecraft version", true);

    public CommandMinecraftAge() {
        super("mcage", false);
    }
    
    @Value
    @RequiredArgsConstructor(onConstructor = @__({@JsonCreator}))
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MinecraftVersion {
        @JsonProperty("id")
        String version;
        @JsonProperty("type")
        String type; // TODO Enum
        @JsonProperty("time")
        Instant time;
        @JsonProperty("releaseTime")
        Instant releaseTime;
    }
    
    @Value
    @RequiredArgsConstructor(onConstructor = @__({@JsonCreator}))
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class VersionManifest {
        @JsonProperty("latest")
        MinecraftVersion latest;
        @JsonProperty("versions")
        List<MinecraftVersion> versions;
    }
    
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    private static final Mono<Map<String, MinecraftVersion>> BY_ID = HttpClient.create()
            .get()
            .uri("https://launchermeta.mojang.com/mc/game/version_manifest.json")
            .responseSingle((resp, buf) -> buf.asString().flatMap(s -> Mono.fromCallable(() -> MAPPER.readValue(s, VersionManifest.class))))
            .flatMapIterable(VersionManifest::getVersions)
            .collectMap(MinecraftVersion::getVersion)
            .cache(Duration.ofMinutes(10));
    
    @Override
    public Mono<?> process(CommandContext ctx) {
        return BY_ID.filter(map -> map.containsKey(ctx.getArg(VERSION)))
                    .map(map -> map.get(ctx.getArg(VERSION)))
                    .map(mv -> Period.between(mv.getReleaseTime().atZone(ZoneOffset.UTC).toLocalDate(), LocalDate.now(ZoneOffset.UTC)))
                    .map(p -> {
                        List<String> components = new ArrayList<>();
                        addComponent(components, p.getYears(), "year");
                        addComponent(components, p.getMonths(), "month");
                        addComponent(components, p.getDays(), "day");
                        final String version = "Minecraft " + ctx.getArg(VERSION);
                        String age;
                        if (components.size() == 0) {
                            return version + " was released today!";
                        }
                        if (components.size() == 1) {
                            age = components.get(0);
                        } else if (components.size() == 2) {
                            age = components.get(0) + " and " + components.get(1);
                        } else {
                            age = String.format("%s, %s, and %s", components.toArray(new Object[0]));
                        }
                        return version + " is **" + age + "** old today.";
                    })
                    .flatMap(ctx::reply)
                    .switchIfEmpty(ctx.error("Unknown version: " + ctx.getArg(VERSION)));
    }
    
    private void addComponent(List<String> components, int amount, String base) {
        if (amount > 0) {
            components.add(amount + " " + base + (amount > 1 ? "s" : ""));
        }
    }
    
    @Override
    public String getDescription(@Nullable Snowflake guildId) {
        return "Tells you the age of a version of Minecraft, in an easily readable and not at all passive aggressive way.";
    }
}
