/*********************************************************************************
 *                                                                               *
 * The MIT License                                                               *
 *                                                                               *
 * Copyright (c) 2015-2020 aoju.org and other contributors.                      *
 *                                                                               *
 * Permission is hereby granted, free of charge, to any person obtaining a copy  *
 * of this software and associated documentation files (the "Software"), to deal *
 * in the Software without restriction, including without limitation the rights  *
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell     *
 * copies of the Software, and to permit persons to whom the Software is         *
 * furnished to do so, subject to the following conditions:                      *
 *                                                                               *
 * The above copyright notice and this permission notice shall be included in    *
 * all copies or substantial portions of the Software.                           *
 *                                                                               *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR    *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,      *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE   *
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER        *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, *
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN     *
 * THE SOFTWARE.                                                                 *
 ********************************************************************************/
package org.aoju.bus.health.hardware.unix.freebsd;

import org.aoju.bus.core.utils.StringUtils;
import org.aoju.bus.health.Builder;
import org.aoju.bus.health.Command;
import org.aoju.bus.health.Memoizer;
import org.aoju.bus.health.hardware.AbstractComputerSystem;
import org.aoju.bus.health.hardware.Baseboard;
import org.aoju.bus.health.hardware.Firmware;

import java.util.function.Supplier;

/**
 * Hardware data obtained from dmidecode.
 *
 * @author Kimi Liu
 * @version 5.8.1
 * @since JDK 1.8+
 */
final class FreeBsdComputerSystem extends AbstractComputerSystem {

    private final Supplier<DmidecodeStrings> readDmiDecode = Memoizer.memoize(this::readDmiDecode);

    @Override
    public String getManufacturer() {
        return readDmiDecode.get().manufacturer;
    }

    @Override
    public String getModel() {
        return readDmiDecode.get().model;
    }

    @Override
    public String getSerialNumber() {
        return readDmiDecode.get().serialNumber;
    }

    @Override
    public Firmware createFirmware() {
        return new FreeBsdFirmware();
    }

    @Override
    public Baseboard createBaseboard() {
        return new FreeBsdBaseboard(readDmiDecode.get().manufacturer, readDmiDecode.get().model,
                readDmiDecode.get().serialNumber, readDmiDecode.get().version);
    }

    private DmidecodeStrings readDmiDecode() {
        String manufacturer = null;
        String model = null;
        String version = null;
        String serialNumber = null;

        // $ sudo dmidecode -t system
        // # dmidecode 3.0
        // Scanning /dev/mem for entry point.
        // SMBIOS 2.7 present.
        //
        // Handle 0x0001, DMI type 1, 27 bytes
        // System Information
        // Manufacturer: Parallels Software International Inc.
        // Product Name: Parallels Virtual Platform
        // Version: None
        // Serial Number: Parallels-47 EC 38 2A 33 1B 4C 75 94 0F F7 AF 86 63 C0
        // C4
        // UUID: 2A38EC47-1B33-854C-940F-F7AF8663C0C4
        // Wake-up Type: Power Switch
        // SKU Number: Undefined
        // Family: Parallels VM
        //
        // Handle 0x0016, DMI type 32, 20 bytes
        // System Boot Information
        // Status: No errors detected

        final String manufacturerMarker = "Manufacturer:";
        final String productNameMarker = "Product Name:";
        final String versionMarker = "Version:";
        final String serialNumMarker = "Serial Number:";

        // Only works with root permissions but it's all we've got
        for (final String checkLine : Command.runNative("dmidecode -t system")) {
            if (checkLine.contains(manufacturerMarker)) {
                manufacturer = checkLine.split(manufacturerMarker)[1].trim();
            }
            if (checkLine.contains(productNameMarker)) {
                model = checkLine.split(productNameMarker)[1].trim();
            }
            if (checkLine.contains(versionMarker)) {
                version = checkLine.split(versionMarker)[1].trim();
            }
            if (checkLine.contains(serialNumMarker)) {
                serialNumber = checkLine.split(serialNumMarker)[1].trim();
            }
        }
        // If we get to end and haven't assigned, use fallback
        if (StringUtils.isBlank(serialNumber)) {
            serialNumber = getSystemSerialNumber();
        }
        return new DmidecodeStrings(manufacturer, model, version, serialNumber);
    }

    private String getSystemSerialNumber() {
        String marker = "system.hardware.serial =";
        for (String checkLine : Command.runNative("lshal")) {
            if (checkLine.contains(marker)) {
                return Builder.getSingleQuoteStringValue(checkLine);
            }
        }
        return Builder.UNKNOWN;
    }

    private static final class DmidecodeStrings {
        private final String manufacturer;
        private final String model;
        private final String version;
        private final String serialNumber;

        private DmidecodeStrings(String manufacturer, String model, String version, String serialNumber) {
            this.manufacturer = StringUtils.isBlank(manufacturer) ? Builder.UNKNOWN : manufacturer;
            this.model = StringUtils.isBlank(model) ? Builder.UNKNOWN : model;
            this.version = StringUtils.isBlank(version) ? Builder.UNKNOWN : version;
            this.serialNumber = StringUtils.isBlank(serialNumber) ? Builder.UNKNOWN : serialNumber;
        }
    }

}
