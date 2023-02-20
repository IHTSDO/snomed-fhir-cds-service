package org.snomed.cdshooks.rest.pojo;

import org.snomed.cdshooks.model.CDSCard;

import java.util.List;

public class CardsResponse {

	private final List<CDSCard> cards;

	public CardsResponse(List<CDSCard> cards) {
		this.cards = cards;
	}

	public List<CDSCard> getCards() {
		return cards;
	}
}
