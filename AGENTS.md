Simple Hubitat App written in Groovy.

The function of this app is to just monitor a single device and calculate the rolling average.

Inputs are:
label for the newly created child device 
The device to monitor 
the attribute to calculate the rolling average (should only support attributes with numeric values: temperature, humidity, illuminance, ultravioletIndex, etc)
total length of time frame to use
Number of data points collect in the time frame

Enable a debug log option

Don't forget the icon metadata, even if it's blank, it's required.

Always update the README.md with basic use instructions and changelog

Always review TODO.md for other bugs to fix and features to implement.

Always review AGENTS.md for baseline function / prompts / instructions
