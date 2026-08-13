package hu.taliann.icesmp.client.projection;

import hu.taliann.icesmp.client.protocol.ClientProtocol;
import hu.taliann.icesmp.client.protocol.PartyStatePayload;
import hu.taliann.icesmp.managers.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PARTY_STATE-projekció a vanilla HUD party-soraival (HudManager.partyMemberLine)
 * azonos adatforrásból. A tag-adat olvasása szándékosan ugyanazon az úton fut, mint
 * a vanilla soré: Bukkit.getPlayer + élő mező-olvasás védőhálóval — Folián a
 * kereszt-régiós olvasás régió-átmenet közben elbukhat, ilyenkor a tag egy tickre
 * healthKnown=false-szal utazik (a vanilla sor ekkor mutat „…”-t). A néző
 * region-szálán hívandó, a HUD-tick cadence-én.
 */
public final class ClientPartyProjector {

    private ClientPartyProjector() {
    }

    public static PartyStatePayload project(final Player viewer, final PartyManager parties) {
        final PartyManager.Party party = parties.getParty(viewer.getUniqueId());
        if (party == null) {
            return new PartyStatePayload(parties.getMaxSize(), List.of());
        }
        final List<PartyStatePayload.Member> members = new ArrayList<>(party.size());
        for (final UUID memberId : party.getMembers()) {
            if (members.size() >= ClientProtocol.MAX_LIST_ELEMENTS) {
                break;
            }
            members.add(memberFor(party, memberId));
        }
        return new PartyStatePayload(parties.getMaxSize(), members);
    }

    private static PartyStatePayload.Member memberFor(final PartyManager.Party party,
                                                      final UUID memberId) {
        final boolean leader = memberId.equals(party.getLeader());
        final Player member = Bukkit.getPlayer(memberId);
        if (member == null || !member.isOnline()) {
            return new PartyStatePayload.Member(memberId, "", leader, false, false, 0, 0);
        }
        final String name = member.getName();
        try {
            final org.bukkit.attribute.AttributeInstance maxAttr =
                    member.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            final double max = Math.max(1.0D, maxAttr == null ? 20.0D : maxAttr.getValue());
            final double hp = Math.max(0.0D, member.getHealth());
            return new PartyStatePayload.Member(memberId, name, leader, true, true,
                    (int) Math.ceil(hp / 2.0D), (int) Math.ceil(max / 2.0D));
        } catch (final Exception ignored) {
            return new PartyStatePayload.Member(memberId, name, leader, true, false, 0, 0);
        }
    }
}
