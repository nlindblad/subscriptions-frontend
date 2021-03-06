define(['$'], function ($) {
    var model = {
       currency: null,
       country: null
    };

    var set = function (currency, country) {
        model.currency = currency;
        model.country = country;
        refresh();
    };

    var refresh = function () {
        ['currency', 'country'].forEach(function (param) {
            var dataAttr = 'data-' + param;
            $('[' + dataAttr + ']').each(function(el) {
                var $el = $(el);
                if ($el.attr(dataAttr) === model[param]) {
                    $el.show();
                } else {
                    $el.hide();
                    $('input[type="radio"]', $el).each(function(input) {
                        $(input).removeAttr('checked');
                    });
                }
            });
        });

    };

    return {
        set: set
    }
});
