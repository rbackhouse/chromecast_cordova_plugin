/*
* The MIT License (MIT)
* 
* Copyright (c) 2013 Richard Backhouse
* 
* Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
* to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
* and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
* 
* The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
* DEALINGS IN THE SOFTWARE.
*/

var opts = Object.prototype.toString;
function isArray(it) { return opts.call(it) === "[object Array]"; }

var statusListeners = [];
var statusListenStarted = false;
var receiverListeners = [];
var receiverListenStarted = false;

var chromecast = {
	cast: function(mediaurl) {
		cordova.exec(
			function() {
			},
			function(err) {
			},
			"ChromecastPlugin",
			"cast",
			[mediaurl]
		);
	},
	pause: function() {
		cordova.exec(
			function() {
			},
			function(err) {
			},
			"ChromecastPlugin",
			"pause",
			[]
		);
	},
	play: function(position) {
		if (!position) {
			position = 0;
		}
		cordova.exec(
			function() {
			},
			function(err) {
			},
			"ChromecastPlugin",
			"play",
			[position]
		);
	},
	stopCast: function() {
		cordova.exec(
			function() {
			},
			function(err) {
			},
			"ChromecastPlugin",
			"stopCast",
			[]
		);
	},
	addStatusListener: function(listener) {
		statusListeners.push(listener);
		if (!statusListenStarted) {
			cordova.exec(
				function(status) {
					statusListeners.forEach(function(listener) {
						listener(status);
					});
				},
				function(err) {
				},
				"ChromecastPlugin",
				"startStatusListener",
				[]
			);
			statusListenStarted = true;
		}
	},
	removeStatusListener: function(listener) {
		var index = statusListeners.indexOf(listener);
		if (index > -1) {
			statusListeners.slice(index, 1);
		}
	},
	addReceiverListener: function(listener) {
		receiverListeners.push(listener);
		if (!receiverListenStarted) {
			cordova.exec(
				function(receiver) {
					if (receiver.id) {
						receiverListeners.forEach(function(listener) {
							listener(receiver);
						});
					}
				},
				function(err) {
				},
				"ChromecastPlugin",
				"startReceiverListener",
				[]
			);
			receiverListenStarted = true;
		}
	},
	removeReceiverListener: function(listener) {
		var index = statusListeners.indexOf(listener);
		if (index > -1) {
			statusListeners.slice(index, 1);
		}
	},
	setReceiver: function(index) {
		cordova.exec(
			function() {
			},
			function(err) {
			},
			"ChromecastPlugin",
			"setReceiver",
			[index]
		);
	}
}

module.exports = chromecast;
