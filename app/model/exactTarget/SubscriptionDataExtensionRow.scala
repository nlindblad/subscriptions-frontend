package model.exactTarget

import com.gu.memsub.Subscription.Paid
import com.gu.memsub.{GoCardless, PaymentCard, PaymentMethod, Subscription}
import com.gu.services.model.BillingSchedule
import com.typesafe.scalalogging.LazyLogging
import model.SubscriptionData
import org.joda.time.DateTime
import utils.Dates

object SubscriptionDataExtensionRow extends LazyLogging{
  def apply(
      billingSchedule: BillingSchedule,
      paymentMethod: PaymentMethod,
      subscription: Subscription with Paid,
      subscriptionData: SubscriptionData
      ): SubscriptionDataExtensionRow = {

    val billingPeriod = subscription.plan.billingPeriod

    val personalData = subscriptionData.personalData

    val address = personalData.address

    val paymentFields = paymentMethod match {
      case GoCardless(mandateId, holder, accountNumber, sortCode) =>
        Seq(
          "Account number" -> formatAccountNumber(accountNumber),
          "Sort Code" -> formatSortCode(sortCode),
          "Account Name" -> holder,
          "Default payment method" -> "Direct Debit",
          "MandateID" -> mandateId
        )
      case _: PaymentCard =>
        Seq("Default payment method" -> "Credit/Debit Card")
    }

    SubscriptionDataExtensionRow(
      personalData.email,
      Seq(
        "ZuoraSubscriberId" -> subscription.name.get,
        "SubscriberKey" -> personalData.email,
        "EmailAddress" -> personalData.email,
        "Subscription term" -> billingPeriod.noun,
        "Payment amount" -> "%.2f".format(subscription.recurringPrice.amount),
        "First Name" -> personalData.first,
        "Last Name" -> personalData.last,
        "Address 1" -> address.lineOne,
        "Address 2" -> address.lineTwo,
        "City" -> address.town,
        "Post Code" -> address.postCode,
        "Country" -> address.country.name,
        "Date of first payment" -> formatDate(billingSchedule.first.date),
        "Currency" -> personalData.currency.glyph,
        //TODO to remove, hardcoded in the template
        "Trial period" -> "14",
        "Email" -> personalData.email
      ) ++ paymentFields
    )
  }

  private def formatDate(dateTime: DateTime) = {
    val day = dateTime.dayOfMonth.get

    val dayWithSuffix = Dates.getOrdinalDay(day)

    val month = dateTime.monthOfYear.getAsText

    val year = dateTime.year.getAsString

    s"$dayWithSuffix $month $year"
  }

  private def formatAccountNumber(AccountNumber: String): String = {
    val lastFour = AccountNumber takeRight 4
    s"****$lastFour"
  }

  private def formatSortCode(sortCode: String): String =
    sortCode.filter(_.isDigit).grouped(2).mkString("-")
}

trait DataExtensionRow {
  def fields: Seq[(String, String)]
}

case class SubscriptionDataExtensionRow(email: String, fields: Seq[(String, String)]) extends DataExtensionRow
