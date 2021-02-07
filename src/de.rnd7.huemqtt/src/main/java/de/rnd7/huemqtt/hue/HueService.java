package de.rnd7.huemqtt.hue;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import de.rnd7.huemqtt.hue.api.HueAbstraction;
import de.rnd7.mqttgateway.Message;
import de.rnd7.mqttgateway.TopicCleaner;
import io.github.zeroone3010.yahueapi.AmbientLightSensor;
import io.github.zeroone3010.yahueapi.DaylightSensor;
import io.github.zeroone3010.yahueapi.Light;
import io.github.zeroone3010.yahueapi.PresenceSensor;
import io.github.zeroone3010.yahueapi.Room;
import io.github.zeroone3010.yahueapi.Switch;
import io.github.zeroone3010.yahueapi.TemperatureSensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HueService {
    private static HueService instance;
    private final HueAbstraction hue;
    private final String baseTopic;
    private ImmutableList<HueDevice> devices = ImmutableList.of();
    private static final Logger LOGGER = LoggerFactory.getLogger(HueService.class);
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    private HueService(final HueAbstraction hue, final String baseTopic) {
        this.hue = hue;
        this.baseTopic = baseTopic;

        scan();
    }

    @Subscribe
    public void onMessage(final Message message) {
        for (Device device : devices) {
            if (device.apply(message)) {
                return;
            }
        }
    }

    public static void shutdown() {
        instance.executor.shutdown();
        instance = null;
    }

    public static HueService start(final HueAbstraction hue, final String baseTopic) {
        if (instance != null) {
            throw new IllegalStateException("Hue service cannot be started twice");
        }

        instance = new HueService(hue, baseTopic);

        instance.executor.scheduleWithFixedDelay(instance::poll, 1500, 500, TimeUnit.MILLISECONDS);
        instance.executor.scheduleAtFixedRate(instance::scan, 1, 1, TimeUnit.HOURS);

        return instance;
    }

    public static void refresh() {
        if (instance != null) {
            instance.hue.refresh();
        }
    }

    public void poll() {
        try {
            hue.refresh();

            devices.forEach(HueDevice::triggerUpdate);
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void scan() {
        try {
            final List<HueDevice> nextDevices = new ArrayList<>();

            for (final Room room : hue.getRooms()) {
                for (Light light : room.getLights()) {
                    final String topic = baseTopic + "/light/" + TopicCleaner.clean(room.getName() + "/" + light.getName());
                    nextDevices.add(new LightDevice(light, topic, topic));
                }
            }

            for (final Light light : hue.getUnassignedLights()) {
                final String topic = baseTopic + "/light/" + TopicCleaner.clean(light.getName());
                nextDevices.add(new LightDevice(light, topic, topic));
            }

            for (final Switch hueSwitch : hue.getSwitches()) {
                final String topic = baseTopic + "/switch/" + TopicCleaner.clean(hueSwitch.getName());
                nextDevices.add(new SwitchDevice(hueSwitch, topic, hueSwitch.getId()));
            }

            for (final DaylightSensor sensor : hue.getDaylightSensors()) {
                final String topic = baseTopic + "/daylight/" + TopicCleaner.clean(sensor.getName());
                nextDevices.add(new DaylightSensorDevice(sensor, topic, sensor.getId()));
            }

            for (final PresenceSensor sensor : hue.getPresenceSensors()) {
                final String topic = baseTopic + "/presence/" + TopicCleaner.clean(sensor.getName());
                nextDevices.add(new PresenceSensorDevice(sensor, topic, sensor.getId()));
            }

            for (final AmbientLightSensor sensor : hue.getAmbientLightSensors()) {
                final String topic = baseTopic + "/ambient/" + TopicCleaner.clean(sensor.getName());
                nextDevices.add(new AmbientLightSensorDevice(sensor, topic, sensor.getId()));
            }

            for (final TemperatureSensor sensor : hue.getTemperatureSensors()) {
                final String topic = baseTopic + "/temperature/" + TopicCleaner.clean(sensor.getName());
                nextDevices.add(new TemperatureSensorDevice(sensor, topic, sensor.getId()));
            }

            devices = ImmutableList.copyOf(nextDevices);
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public ImmutableList<HueDevice> getDevices() {
        return devices;
    }

    public <T extends HueDevice> T getDevice(String id, Class<T> type) {
        return devices.stream().filter(d -> d.getId().equals(id))
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst()
            .orElseThrow(IllegalStateException::new);
    }
}
