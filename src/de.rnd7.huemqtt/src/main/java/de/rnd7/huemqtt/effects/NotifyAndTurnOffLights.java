package de.rnd7.huemqtt.effects;

import io.github.zeroone3010.yahueapi.Light;
import io.github.zeroone3010.yahueapi.State;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NotifyAndTurnOffLights {
    private final Light light;
    private final ColorXY[] notificationColors;

    public NotifyAndTurnOffLights(final Light room, final ColorXY... notificationColors) {
        this.light = room;
        this.notificationColors = notificationColors;
    }

    public void notify(final Duration duration) {
        new Thread(() -> notifySync(duration)).start();
    }

    public void notifySync(final Duration duration) {
        LightHelper.withOff(() -> {
            final List<Runnable> tasks = Stream.of(this.notificationColors)
                .map(color -> (Runnable) () -> turnOn(color))
                .collect(Collectors.toList());

            LightHelper.processTasksWithPostDelay(tasks, duration);
        }, this.light);
    }


    private void turnOn(final ColorXY notificationColor) {
        this.light.setState(State.builder()
            .xy(notificationColor.getXY())
            .brightness(254)
            .on());
    }

}
