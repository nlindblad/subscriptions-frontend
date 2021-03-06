/*
*   Usage:
*   el.addEventListener('keyup', masker(' ', 4)) // cc
*   el.addEventListener('keyup', masker(' / ', 2)) // date
*   el.addEventListener('keyup', masker('', 4)) // cvc
*/
define(function () {
    'use strict';

    return function(delim, len) {
        var tokRegex = new RegExp('\\d{1,' + len + '}', 'g');
        var validRegex = new RegExp('\\d|(' + delim + ')|^', 'g');
        var delimRegex = new RegExp(delim, 'g');
        var prevValue = '';

        return function () {
            var toks = this.value.replace(delimRegex, '').match(tokRegex);
            var value = '';

            if (toks && this.selectionEnd === this.value.length) {
                if (this.value.length >= prevValue.length
                    && toks[toks.length - 1].length === len) {
                    toks.push('');
                }

                value = toks.join(delim).slice(0, this.maxLength);
            } else {
                value = this.value.match(validRegex).join('');
            }

            if (value !== this.value) {
                this.value = value;
            }

            prevValue = this.value;
        };
    };

});
