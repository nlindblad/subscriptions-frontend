define([
    'modules/forms/toggleError',
    'utils/ajax',
    'modules/checkout/formElements',
    'modules/checkout/countryChoice',
    'bean',
    '$'
], function (toggleError, ajax, formElements, countryChoice, bean, $) {
    'use strict';

    var getValidity = function () {

        var promoCode = formElements.$PROMO_CODE[0].value.trim();
        if (promoCode == '') {
            return;
        }
        console.log(promoCode);
        ajax({
            method: 'GET',
            url: '/checkout/lookupPromotion', //fixme: get this endpoint actually *from* somewhere
            data: {
                promoCode: promoCode,
                productRatePlanId: formElements.getRatePlanId(),
                country: countryChoice.getCurrentCountryOption().value
            }
        }).then(function (a) {

            toggleError(formElements.$PROMO_CODE, false);

            $('.js-promo-code-message').text(a.promotion.description);
            $('.js-promo-code-image').src=a.thumbnailUrl;
        }).catch(function (a) {
            //I think that I've made some mistake with the html which is causing this toggleError to never really set an error
            toggleError(formElements.$PROMO_CODE, true);
            var b = JSON.parse(a.response);
            console.log(b);

        })
    };

    return {
        init: function () {
            getValidity();
            bean.on(formElements.$COUNTRY_SELECT[0], 'change', getValidity);
            bean.on($('.js-checkout-promo-code-validate')[0], 'click', getValidity);

        }
    };
});
