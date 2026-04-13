package io.hyperswitch.capacitor;

import com.getcapacitor.Logger;

public class Hyperswitch {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
}
