package com.sodiumcow.cc.shell;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.sodiumcow.uri.unify.Unify;
import com.sodiumcow.uri.unify.json.Share;
import com.sodiumcow.util.DB;
import com.sodiumcow.util.S;

public class UnifyShell {
    
    private DB db;
    public UnifyShell(DB db) {
        this.db = db;
    }

    private void connect() throws SQLException {
        db.connect();
    }

    public Share.Collaborator[] getUsers() throws SQLException {
        connect();
        DB.Selection.Result list = db.new Selection("UTUser",
                                                    "email firstName lastName company",
                                                    "isExternalContact",
                                                    0)    .rows();
        Share.Collaborator[] users = new Share.Collaborator[list.rows.length];
        for (int i=0; i<users.length; i++) {
            users[i] = new Share.Collaborator();
            users[i].email     = list.rows[i][0];
            users[i].firstName = list.rows[i][1];
            users[i].lastName  = list.rows[i][2];
            users[i].company   = list.rows[i][3];
        }
        return users;
    }

    /*------------------------------------------------------------------------*
     * Lying cheating Unify password getter.                                  *
     *------------------------------------------------------------------------*/
    
    public Unify getUnify(String email) {
        String password = "cleo"; // just a hack default
        try {
            int eid = -1;
            DB.Selection.Result contact = db.new Selection("VLContact",
                                                           "VLEntityID",
                                                           "VLContactNum Value",
                                                           0, email).rows();
            if (contact.count>0) {
                eid = Integer.valueOf(contact.rows[0][0]);
                DB.Selection.Result user = db.new Selection("VLUser",
                                                            "UserPassword LDAPUser",
                                                            "VLEntityID",
                                                            eid).rows();
                if (user.count>0) {
                    if (user.rows[0][1].equals("0")) {
                        password = Util.decode(user.rows[0][0]);
                    } else {
                        // LDAP user -- no password
                    }
                } else {
                    // shouldn't happen -- no VLUser found
                }
            } else {
                // email address not found
            }
        } catch (Exception e) {
            // error("could not find "+email+" in Unify DB", e);
        }
        // ok let's do it
        return Unify.getUnify(email, password);
    }

    public Share[] getShares(Share.Collaborator user) {
        Unify u = getUnify(user.email);
        return u.getShares();
    }

    public void newShare(String email, String share) throws Exception {
        // 326 = UTSearchableEntity id of "email"
        // insert into 144 = UTShareConfiguration (dateCreated, dateExpires, fileSize, megaByteSize, share_id) values ('2014-11-24 18:59:02', null, 0, 2048, null)
        // insert into 144 = UTShareEntity (name, parent_id, trashEntity_id, type) values ('My Folder', null, null, 'SHARE')
        // insert into UTShare (fromTrust, lastUpdatedBy, lastUpdatedDate, owner_collaboratorEntity_id, shareConfiguration_id, trashEntity_trashEntity_id, id)
        //   values (0, 'aa2@cleo.demo', '2014-11-24 18:59:02', null, 144, null, 144)
        // insert into 327 = UTSearchableEntity (entityType, searchableCriteria) values ('Collaborator', null)
        // insert into UTCollaboratorEntity (expiration, share_id, type, entity_id) values (null, 144, 'User', 327)
        // insert into UTCollaboratorUser (dtLastRead, user_entity_id, collaboratorEntity_id) values (null, 326, 327)
        // insert into UTCollaboratorPermission (collaborator_entity_id, enabled, type) values (327, 1, 'MODIFY_CONFIGURATION')
        // insert into 144 = UTDiscussion (createdByEmail, dateCreated, dateLastUpdated, name, share_id)
        //  values ('aa2@cleo.demo', '2014-11-24 18:59:02', '2014-11-24 18:59:02', 'General Discussion', 144)
        // insert into 328 = UTSearchableEntity (entityType, searchableCriteria) values ('ContactGroup', 'All Contacts')
        // insert into UTContactGroup (name, parent_entity_id, user_id, entity_id) values ('All Contacts', null, 326, 328)
        // update UTShareConfiguration set dateCreated='2014-11-24 18:59:02', dateExpires=null, fileSize=0, megaByteSize=2048, share_id=144 where id=144
        try {
            DB.Selection.Result id = db.new Selection("UTUser", "entity_id", "email", email).rows();
            if (id.count==0) throw new Exception("user for email address "+email+" not found");
            int user_id = Integer.valueOf(id.rows[0][0]);
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            int config_id = db.insert("UTShareConfiguration", "dateCreated fileSize megaByteSize", now, 0, 2048);
            int share_id = db.insert("UTShareEntity", "name type", share, "SHARE");
            db.insert("UTShare", "fromTrust lastUpdatedBy lastUpdatedDate shareConfiguration_id id",
                                 0, email, now, config_id, share_id);
            int collab_id = db.insert("UTSearchableEntity", "entityType", "Collaborator");
            db.insert("UTCollaboratorEntity", "share_id type entity_id", share_id, "User", collab_id);
            db.insert("UTCollaboratorUser", "user_entity_id collaboratorEntity_id", user_id, collab_id);
            db.insert("UTCollaboratorPermission", "collaborator_entity_id enabled type", collab_id, 1, "FULL_CONTROL");
            for (Share.Permission perm : Share.Permission.values()) {
                db.insert("UTCollaboratorPermission", "collaborator_entity_id enabled type", collab_id, 1, perm.name());
            }
            db.insert("UTDiscussion", "createdByEmail dateCreated dateLastUpdated name share_id",
                                      email, now, now, "General Discussion", share_id);
            int group_id = db.insert("UTSearchableEntity", "entityType searchableCriteria", "ContactGroup", "All Contacts");
            db.insert("UTContactGroup", "name user_id entity_id", "All Contacts", user_id, group_id);
            db.new Selection("UTShareConfiguration", "id", config_id).update("share_id", share_id);
        } catch (SQLException e) {
            // ?
            throw new Exception("Failed inserting "+email, e);
        }

        //Unify u = getUnify(email);
        //return u.newShare(share);
    }

    public Share.Collaborator[] getCollaborators(Share.Collaborator user, Share share) {
        Unify u = getUnify(user.email);
        return u.getCollaborators(share.share.id);
    }

    public static class NamePerms {
        public String            name;
        public Share.Permissions perms;
        public NamePerms(String name, Share.Permissions perms) {
            this.name  = name;
            this.perms = perms;
        }
    }
    private static final EnumSet<Share.Permission> OWNER_ONLY =
            EnumSet.of(Share.Permission.ADD_COLLABORATORS,
                       Share.Permission.DELETE_SHARE,
                       Share.Permission.MODIFY_COLLABORATORS,
                       Share.Permission.MODIFY_CONFIGURATION);

    public void sync(boolean list) throws SQLException {
        Map<String,Set<String>>  groups  = new TreeMap<String,Set<String>> (); // group email -> set of user email
        Map<Long,Set<NamePerms>> folders = new HashMap<Long,Set<NamePerms>>(); // folder id   -> set of group email+perms
        Map<Long,String>         owner   = new HashMap<Long,String>        (); // folder id   -> owner with FULL_CONTROL
        for (Share.Collaborator user : getUsers()) {
            // list the shares for this user
            // we are looking for one called "Members" with FULL_CONTROL
            Share[] shares = getShares(user);
            List<Share> membershare = S.filter(shares, new S.Filter<Share>() {
                @Override public boolean accept(Share s) {
                    return s.share.name.equals("Members") && s.permissions.isAll();
                }});
            if (membershare!=null && membershare.size()>0) {
                // ok -- its a group
                // add the group to the groups map and create a member set
                String group = user.email;
                Set<String> members = new HashSet<String>();
                for (Share.Collaborator c : getCollaborators(user, membershare.get(0))) {
                    if (!c.email.equals(group)) {
                        members.add(c.email);
                    }
                }
                groups.put(group, members);
                // now put the remaining folders in the shared folders map
                for (Share s : shares) {
                    if (!s.share.name.equals("Members")) {
                        Share.Permissions memberPermissions = new Share.Permissions(s.permissions.getPermissions());
                        memberPermissions.removeAll(OWNER_ONLY);
                        if (!folders.containsKey(s.share.id)) {
                            folders.put(s.share.id, new HashSet<NamePerms>());
                        }
                        folders.get(s.share.id).add(new NamePerms(group,memberPermissions));
                        if (!owner.containsKey(s.share.id) && s.permissions.isAll()) {
                            owner.put(s.share.id, group);
                        }
                    }
                }
            }
        }

        // Now we have a complete list of groups and their members
        // and shared folders and their groups.
        // We now reconcile for each folder
        if (list) {
            for (Map.Entry<String,Set<String>> e : groups.entrySet()) {
                System.out.println("group "+e.getKey()+" members "+S.join(", ", e.getValue()));
            }
        }

        // Walk through the folder list building up expected membership (and perms)
        for (Map.Entry<Long,Set<NamePerms>> e : folders.entrySet()) {
            if (e.getValue().size() < 2) {
                // unshared group folder -- forget it
                continue;
            }
            long folderID = e.getKey();
            Map<String,Share.Permissions> members = new HashMap<String,Share.Permissions>();
            for (NamePerms np : e.getValue()) {
                String group = np.name;
                EnumSet<Share.Permission> perms = np.perms.getPermissions();
                for (String member : groups.get(group)) {
                    if (members.containsKey(member)) {
                        members.get(member).addAll(perms);
                    } else {
                        members.put(member, new Share.Permissions(perms));
                    }
                }
            }
            // now members is a list of expected members and their permissions
            Unify unify = Unify.getUnify(owner.get(folderID), "cleo");
            Share.Collaborator[] existing = unify.getCollaborators(folderID);
            String prefix;
            if (list) {
                StringBuffer s = new StringBuffer();
                s.append("folder id ").append(folderID).append(" groups");
                for (NamePerms np : e.getValue()) {
                    s.append(" ").append(np.name).append(" ").append(np.perms.toString());
                }
                s.append(" owner ").append(owner.get(folderID));
                System.out.println(s.toString());
                prefix = "members  ";
                for (Map.Entry<String,Share.Permissions> member : members.entrySet()) {
                    System.out.println(prefix+member.getKey()+" "+member.getValue());
                    prefix = "         ";
                }
            }
            prefix = "existing ";
            for (int i=0; i<existing.length; i++) {
                if (!groups.containsKey(existing[i].email)) {
                    existing[i] = unify.getCollaborator(folderID, existing[i].id); // need to get permissions
                    if (list) {
                        System.out.println(prefix+existing[i].email+" "+existing[i].permissions);
                        prefix = "         ";
                    }
                }
            }
            // remove items that match
            List<Share.Collaborator> extra = new ArrayList<Share.Collaborator>();
            List<Share.Collaborator> update = new ArrayList<Share.Collaborator>();
            for (Share.Collaborator exist : existing) {
                // skip groups
                if (groups.containsKey(exist.email)) {
                    continue;
                }
                // process user members
                if (members.containsKey(exist.email)) {
                    if (members.get(exist.email).equals(exist.permissions)) {
                        members.remove(exist.email);
                    } else {
                        exist.permissions = members.get(exist.email);
                        update.add(exist);
                    }
                } else {
                    extra.add(exist);
                }
            }
            // now extra is for deletion, update for update, and remaining members to add
            if (extra.size()>0) {
                prefix = "delete:  ";
                for (Share.Collaborator delete : extra) {
                    System.out.println(prefix+delete.email);
                    prefix = "         ";
                    if (!list) unify.deleteCollaborator(folderID, delete.id);
                }
            }
            prefix = "add:     ";
            for (Map.Entry<String,Share.Permissions> member : members.entrySet()) {
                System.out.println(prefix+member.getKey()+" "+member.getValue());
                prefix = "         ";
                Share.Collaborator add = new Share.Collaborator();
                add.email = member.getKey();
                add.permissions = member.getValue();
                if (!list) unify.newCollaborator(folderID, add);
            }
            prefix = "update:  ";
            for (Share.Collaborator change : update) {
                System.out.println(prefix+change.email+" "+change.permissions);
                prefix = "         ";
                if (!list) unify.updateCollaborator(folderID, change);
            }
        }
    }
}
