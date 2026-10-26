from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from .announcement import AmountError, parse_amount


class GenerateSpeechRequest(BaseModel):
    """The client chooses language, gender, currency and amount; the server chooses the voice and text."""

    model_config = ConfigDict(extra="forbid")

    language: Literal["en-US", "km-KH"]
    gender: Literal["male", "female"]
    currency: Literal["USD", "KHR"] = "USD"
    amount: str = Field(..., max_length=16, description='Decimal string, e.g. "5.75" (USD) or "10000" (KHR)')

    @model_validator(mode="after")
    def _validate_amount(self):
        try:
            parse_amount(self.amount, self.currency)
        except AmountError as e:
            raise ValueError("amount: {}".format(e))
        return self

    @property
    def amount_minor_units(self) -> int:
        return parse_amount(self.amount, self.currency)
