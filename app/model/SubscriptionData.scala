package model

import com.gu.i18n.{Country, CountryGroup}
import com.gu.identity.play.IdUser
import com.gu.memsub.Subscription.ProductRatePlanId
import com.gu.memsub.{FullName, Address}
import com.gu.memsub.promo.PromoCode
import IdUserOps._

sealed trait PaymentType {
  def toKey: String
}

object PaymentType {
  def fromKey(key: String): Option[PaymentType] = key match {
    case "card" => Some(CreditCard)
    case "direct-debit" => Some(DirectDebit)
    case _ => None
  }
}

case object DirectDebit extends PaymentType {
  override def toKey = "direct-debit"
}

case object CreditCard extends PaymentType {
  override def toKey = "card"
}

sealed trait PaymentData

case class DirectDebitData(account: String, sortCodeValue: String, holder: String) extends PaymentData {
  val sortCode = sortCodeValue.filter(_.isDigit)
}
case class CreditCardData(stripeToken: String) extends PaymentData

case class PersonalData(first: String,
                        last: String,
                        email: String,
                        receiveGnmMarketing: Boolean,
                        address: Address,
                        telephoneNumber: Option[String] = None
                        ) extends FullName {
  def fullName = s"$first $last"

  private lazy val countryName = address.countryName

  private lazy val notFound =
    throw new NoSuchElementException(s"Could not find a country group for country with name $countryName")

  private lazy val countryGroup: CountryGroup =
    CountryGroup.byCountryNameOrCode(countryName)
      .getOrElse(notFound)

  lazy val currency = countryGroup.currency

  lazy val country: Country =
    CountryGroup.countryByNameOrCode(countryName)
      .getOrElse(notFound)
}


case class SubscriptionData(
                             personalData: PersonalData,
                             paymentData: PaymentData,
                             productRatePlanId: ProductRatePlanId,
                             suppliedPromoCode: Option[PromoCode])

object SubscriptionData {
  def fromIdUser(promoCode: Option[PromoCode])(u: IdUser) = {
    val personalData = PersonalData(
      first = u.privateFields.flatMap(_.firstName).getOrElse(""),
      last = u.privateFields.flatMap(_.secondName).getOrElse(""),
      email = u.primaryEmailAddress,
      receiveGnmMarketing = u.statusFields.flatMap(_.receiveGnmMarketing).getOrElse(false),
      address = u.address,
      telephoneNumber = u.privateFields.flatMap(_.telephoneNumber).flatMap(_.localNumber)
    )

    val blankPaymentData = DirectDebitData("", "", "")

    val blankRatePlanId = ProductRatePlanId("") // this makes me very nervous indeed

    SubscriptionData(personalData, blankPaymentData, blankRatePlanId, promoCode)


  }

}
